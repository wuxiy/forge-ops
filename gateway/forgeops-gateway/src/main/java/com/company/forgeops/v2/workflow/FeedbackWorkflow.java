package com.company.forgeops.v2.workflow;

import com.company.forgeops.v2.audit.AuditTrail;
import com.company.forgeops.v2.feedback.domain.ContextSnapshot;
import com.company.forgeops.v2.feedback.domain.ContextSnapshotRepository;
import com.company.forgeops.v2.feedback.domain.Feedback;
import com.company.forgeops.v2.feedback.domain.FeedbackCycle;
import com.company.forgeops.v2.feedback.domain.FeedbackCycleRepository;
import com.company.forgeops.v2.feedback.domain.FeedbackRepository;
import com.company.forgeops.v2.feedback.domain.ProjectFeedbackCounter;
import com.company.forgeops.v2.feedback.domain.ProjectFeedbackCounterRepository;
import com.company.forgeops.v2.integration.domain.OutboxEvent;
import com.company.forgeops.v2.integration.domain.OutboxEventRepository;
import jakarta.persistence.OptimisticLockException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only module allowed to change Feedback state or select its current Cycle.
 * External integrations write facts and request this module; they do not mutate entities directly.
 */
@Service
public class FeedbackWorkflow {

    private static final String CONTEXT_SCHEMA_VERSION = "v2";

    private final FeedbackRepository feedbacks;
    private final FeedbackCycleRepository cycles;
    private final ContextSnapshotRepository snapshots;
    private final ProjectFeedbackCounterRepository counters;
    private final OutboxEventRepository outboxEvents;
    private final AuditTrail auditTrail;

    public FeedbackWorkflow(FeedbackRepository feedbacks, FeedbackCycleRepository cycles,
            ContextSnapshotRepository snapshots, ProjectFeedbackCounterRepository counters, OutboxEventRepository outboxEvents,
            AuditTrail auditTrail) {
        this.feedbacks = feedbacks;
        this.cycles = cycles;
        this.snapshots = snapshots;
        this.counters = counters;
        this.outboxEvents = outboxEvents;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public Feedback submit(SubmitFeedbackCommand command) {
        validate(command);
        long displayNo = nextDisplayNo(command.projectId());
        UUID feedbackId = UUID.randomUUID();
        FeedbackCycle cycle = FeedbackCycle.initial(feedbackId, command.reporterSubject());
        Feedback feedback = Feedback.create(feedbackId, command.projectId(), displayNo, command.reporterSubject(),
                command.title(), command.description(), cycle.getId());
        feedback = feedbacks.save(feedback);
        // feedback.current_cycle_id is a deferred FK, so the stable Feedback identity exists before Cycle 1.
        cycles.save(cycle);
        snapshots.save(ContextSnapshot.create(feedback.getId(), cycle.getId(), CONTEXT_SCHEMA_VERSION, command.contextSha256(),
                command.redactionCount(), command.redactedContextJson()));
        feedback.transitionTo(FeedbackState.CONTEXT_READY);
        enqueue(feedback, "FEEDBACK_SUBMITTED");
        auditTrail.record(feedback.getId(), cycle.getId(), null, command.reporterSubject(), "FEEDBACK_SUBMITTED", "SUCCESS",
                command.traceId(), "{\"displayNo\":" + displayNo + "}");
        return feedback;
    }

    @Transactional
    public Feedback transition(UUID feedbackId, FeedbackState target, String actor, String traceId) {
        Feedback feedback = get(feedbackId);
        FeedbackState before = feedback.getState();
        feedback.transitionTo(target);
        enqueue(feedback, "WORKFLOW_STATE_CHANGED");
        auditTrail.record(feedbackId, feedback.getCurrentCycleId(), null, actor, "STATE_TRANSITION", "SUCCESS", traceId,
                "{\"from\":\"" + before + "\",\"to\":\"" + target + "\"}");
        return feedback;
    }

    @Transactional
    public Feedback reopen(UUID feedbackId, String actor, String reason, String redactedContextJson, String contextSha256,
            int redactionCount, String traceId) {
        Feedback feedback = get(feedbackId);
        feedback.transitionTo(FeedbackState.REOPENED);
        int nextCycleNo = cycles.findMaxCycleNo(feedbackId) + 1;
        FeedbackCycle nextCycle = FeedbackCycle.reopen(feedbackId, nextCycleNo, actor, reason);
        cycles.save(nextCycle);
        feedback.moveToCycle(nextCycle.getId());
        snapshots.save(ContextSnapshot.create(feedbackId, nextCycle.getId(), CONTEXT_SCHEMA_VERSION, contextSha256,
                redactionCount, redactedContextJson));
        feedback.transitionTo(FeedbackState.CONTEXT_READY);
        enqueue(feedback, "FEEDBACK_REOPENED");
        auditTrail.record(feedbackId, nextCycle.getId(), null, actor, "FEEDBACK_REOPENED", "SUCCESS", traceId,
                "{\"cycleNo\":" + nextCycleNo + "}");
        return feedback;
    }

    private Feedback get(UUID feedbackId) {
        return feedbacks.findById(feedbackId)
                .orElseThrow(() -> new IllegalArgumentException("Feedback does not exist: " + feedbackId));
    }

    private long nextDisplayNo(String projectId) {
        try {
            ProjectFeedbackCounter counter = counters.findByProjectId(projectId)
                    .orElseGet(() -> counters.saveAndFlush(ProjectFeedbackCounter.start(projectId)));
            return counter.next();
        } catch (DataIntegrityViolationException concurrentFirstCounter) {
            throw new OptimisticLockException("Concurrent counter initialization for project " + projectId,
                    concurrentFirstCounter);
        }
    }

    private void enqueue(Feedback feedback, String eventType) {
        String idempotencyKey = feedback.getId() + "/" + feedback.getCurrentCycleId() + "/" + feedback.getVersion()
                + "/" + eventType + "/" + feedback.getState();
        String payload = "{\"feedbackId\":\"" + feedback.getId() + "\",\"cycleId\":\""
                + feedback.getCurrentCycleId() + "\",\"state\":\"" + feedback.getState() + "\"}";
        outboxEvents.save(OutboxEvent.pending("FEEDBACK", feedback.getId(), eventType, idempotencyKey, payload));
    }

    private static void validate(SubmitFeedbackCommand command) {
        if (command == null || command.projectId() == null || command.projectId().isBlank()
                || command.reporterSubject() == null || command.reporterSubject().isBlank()
                || command.title() == null || command.title().isBlank()
                || command.description() == null || command.description().isBlank()
                || command.redactedContextJson() == null || command.contextSha256() == null) {
            throw new IllegalArgumentException("A complete feedback command is required");
        }
    }
}
