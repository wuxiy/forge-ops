package com.company.forgeops.v2.workflow;

import com.company.forgeops.v2.audit.AuditTrail;
import com.company.forgeops.v2.agent.domain.AgentRole;
import com.company.forgeops.v2.agent.domain.AgentRun;
import com.company.forgeops.v2.agent.domain.AgentRunRepository;
import com.company.forgeops.v2.agent.domain.AgentRunState;
import com.company.forgeops.v2.agent.execution.AgentContracts.TriageDecision;
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
import com.company.forgeops.v2.verification.domain.VerificationRecord;
import com.company.forgeops.v2.verification.domain.VerificationRecordRepository;
import com.company.forgeops.v2.verification.domain.VerificationResult;
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
    private final AgentRunRepository agentRuns;
    private final VerificationRecordRepository verifications;
    private final AuditTrail auditTrail;

    public FeedbackWorkflow(FeedbackRepository feedbacks, FeedbackCycleRepository cycles,
            ContextSnapshotRepository snapshots, ProjectFeedbackCounterRepository counters, OutboxEventRepository outboxEvents,
            AgentRunRepository agentRuns, VerificationRecordRepository verifications, AuditTrail auditTrail) {
        this.feedbacks = feedbacks;
        this.cycles = cycles;
        this.snapshots = snapshots;
        this.counters = counters;
        this.outboxEvents = outboxEvents;
        this.agentRuns = agentRuns;
        this.verifications = verifications;
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
        queueTriage(feedback, command.traceId());
        auditTrail.record(feedback.getId(), cycle.getId(), null, command.reporterSubject(), "FEEDBACK_SUBMITTED", "SUCCESS",
                command.traceId(), "{\"displayNo\":" + displayNo + "}");
        return feedback;
    }

    @Transactional
    public Feedback transition(UUID feedbackId, FeedbackState target, String actor, String traceId) {
        Feedback feedback = get(feedbackId);
        FeedbackState before = feedback.getState();
        feedback.transitionTo(target);
        auditTrail.record(feedbackId, feedback.getCurrentCycleId(), null, actor, "STATE_TRANSITION", "SUCCESS", traceId,
                "{\"from\":\"" + before + "\",\"to\":\"" + target + "\"}");
        return feedback;
    }

    @Transactional
    public Feedback reopen(UUID feedbackId, String actor, String reason, String redactedContextJson, String contextSha256,
            int redactionCount, String traceId) {
        Feedback feedback = get(feedbackId);
        return reopenCurrentCycle(feedback, actor, reason, redactedContextJson, contextSha256, redactionCount, traceId);
    }

    /** The reporter's PASS or REOPEN is the only path out of WAITING_VERIFY. */
    @Transactional
    public Feedback recordVerification(UUID feedbackId, String verifierSubject, VerificationResult result, String comment,
            String redactedContextJson, String contextSha256, int redactionCount, String traceId) {
        if (verifierSubject == null || verifierSubject.isBlank() || result == null) {
            throw new IllegalArgumentException("Verifier subject and result are required");
        }
        Feedback feedback = get(feedbackId);
        if (feedback.getState() != FeedbackState.WAITING_VERIFY) {
            throw new WorkflowConflictException("Feedback is not ready for verification: " + feedback.getState());
        }
        UUID verifiedCycleId = feedback.getCurrentCycleId();
        verifications.save(VerificationRecord.create(feedback.getId(), verifiedCycleId, verifierSubject, result, comment));
        if (result == VerificationResult.PASS) {
            feedback.transitionTo(FeedbackState.DONE);
            auditTrail.record(feedback.getId(), verifiedCycleId, null, verifierSubject, "VERIFICATION_RECORDED", "SUCCESS",
                    traceId, "{\"result\":\"PASS\"}");
            return feedback;
        }
        validateReopenContext(comment, redactedContextJson, contextSha256, redactionCount);
        auditTrail.record(feedback.getId(), verifiedCycleId, null, verifierSubject, "VERIFICATION_RECORDED", "SUCCESS",
                traceId, "{\"result\":\"REOPEN\"}");
        return reopenCurrentCycle(feedback, verifierSubject, comment, redactedContextJson, contextSha256, redactionCount, traceId);
    }

    private Feedback reopenCurrentCycle(Feedback feedback, String actor, String reason, String redactedContextJson,
            String contextSha256, int redactionCount, String traceId) {
        validateReopenContext(reason, redactedContextJson, contextSha256, redactionCount);
        feedback.transitionTo(FeedbackState.REOPENED);
        int nextCycleNo = cycles.findMaxCycleNo(feedback.getId()) + 1;
        FeedbackCycle nextCycle = FeedbackCycle.reopen(feedback.getId(), nextCycleNo, actor, reason);
        cycles.save(nextCycle);
        feedback.moveToCycle(nextCycle.getId());
        snapshots.save(ContextSnapshot.create(feedback.getId(), nextCycle.getId(), CONTEXT_SCHEMA_VERSION, contextSha256,
                redactionCount, redactedContextJson));
        feedback.transitionTo(FeedbackState.CONTEXT_READY);
        queueTriage(feedback, traceId);
        auditTrail.record(feedback.getId(), nextCycle.getId(), null, actor, "FEEDBACK_REOPENED", "SUCCESS", traceId,
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

    @Transactional
    public void recordRuntimeSubmission(UUID agentRunId, String providerRunId, String traceId) {
        AgentRun run = agentRuns.lockById(agentRunId)
                .orElseThrow(() -> new IllegalArgumentException("Agent run does not exist: " + agentRunId));
        if (run.getState() == AgentRunState.RUNNING) {
            if (!providerRunId.equals(run.getProviderRunId())) {
                throw new WorkflowConflictException("Agent run provider id changed for " + agentRunId);
            }
            return;
        }
        if (run.getState() != AgentRunState.QUEUED) {
            throw new WorkflowConflictException("Agent run is not queueable: " + run.getState());
        }
        Feedback feedback = get(run.getFeedbackId());
        FeedbackState expected = run.getRole() == AgentRole.TRIAGE ? FeedbackState.TRIAGE_QUEUED : FeedbackState.CODE_QUEUED;
        FeedbackState target = run.getRole() == AgentRole.TRIAGE ? FeedbackState.TRIAGE_RUNNING : FeedbackState.CODE_RUNNING;
        if (feedback.getState() != expected) {
            throw new WorkflowConflictException("Feedback is not ready for agent run: " + feedback.getState());
        }
        run.markRunning(providerRunId);
        feedback.transitionTo(target);
        auditTrail.record(feedback.getId(), run.getCycleId(), run.getId(), "runtime", "AGENT_SUBMITTED", "SUCCESS", traceId,
                "{\"role\":\"" + run.getRole() + "\"}");
    }

    @Transactional
    public void recordRuntimeFailure(UUID agentRunId, AgentRunState state, String category, String traceId) {
        AgentRun run = agentRuns.lockById(agentRunId)
                .orElseThrow(() -> new IllegalArgumentException("Agent run does not exist: " + agentRunId));
        if (run.getState() == AgentRunState.SUCCEEDED || run.getState() == AgentRunState.FAILED
                || run.getState() == AgentRunState.INVALID_OUTPUT || run.getState() == AgentRunState.CANCELLED
                || run.getState() == AgentRunState.TIMED_OUT) {
            return;
        }
        Feedback feedback = get(run.getFeedbackId());
        FeedbackState target = run.getRole() == AgentRole.TRIAGE ? FeedbackState.TRIAGE_FAILED : FeedbackState.EXECUTION_FAILED;
        run.fail(state, category, "Runtime reported " + state);
        if (feedback.getState() != target) {
            feedback.transitionTo(target);
        }
        auditTrail.record(feedback.getId(), run.getCycleId(), run.getId(), "runtime", "AGENT_TERMINAL", "FAILED", traceId,
                "{\"state\":\"" + state + "\",\"category\":\"" + category + "\"}");
    }

    @Transactional
    public void recordTriageSuccess(UUID agentRunId, TriageDecision decision, String resultJson, String traceId) {
        AgentRun run = agentRuns.lockById(agentRunId)
                .orElseThrow(() -> new IllegalArgumentException("Agent run does not exist: " + agentRunId));
        if (run.getRole() != AgentRole.TRIAGE || run.getState() != AgentRunState.RUNNING) {
            throw new WorkflowConflictException("Triage result is not applicable to run " + agentRunId);
        }
        Feedback feedback = get(run.getFeedbackId());
        if (feedback.getState() != FeedbackState.TRIAGE_RUNNING) {
            throw new WorkflowConflictException("Feedback is not running triage: " + feedback.getState());
        }
        run.succeed(resultJson);
        switch (decision) {
            case NEEDS_INPUT -> feedback.transitionTo(FeedbackState.NEEDS_INPUT);
            case NO_CODE_REQUIRED -> feedback.transitionTo(FeedbackState.NO_CODE_REQUIRED);
            case PROCEED_CODING -> {
                feedback.transitionTo(FeedbackState.CODE_QUEUED);
                queueCoding(feedback, traceId);
            }
        }
        auditTrail.record(feedback.getId(), run.getCycleId(), run.getId(), "runtime", "TRIAGE_RESULT", "SUCCESS", traceId,
                "{\"decision\":\"" + decision + "\"}");
    }

    /** Stores a schema-sanitized Coding declaration but deliberately leaves Feedback in CODE_RUNNING. */
    @Transactional
    public AgentRun recordCodingDeclaration(UUID agentRunId, String resultJson, String traceId) {
        AgentRun run = agentRuns.lockById(agentRunId)
                .orElseThrow(() -> new IllegalArgumentException("Agent run does not exist: " + agentRunId));
        if (run.getRole() != AgentRole.CODING) {
            throw new WorkflowConflictException("Coding declaration is not applicable to run " + agentRunId);
        }
        if (run.getState() == AgentRunState.SUCCEEDED) {
            return run;
        }
        if (run.getState() != AgentRunState.RUNNING) {
            throw new WorkflowConflictException("Coding declaration is not applicable to run " + agentRunId);
        }
        Feedback feedback = get(run.getFeedbackId());
        if (feedback.getState() != FeedbackState.CODE_RUNNING || !feedback.getCurrentCycleId().equals(run.getCycleId())) {
            throw new WorkflowConflictException("Feedback is not running coding for this cycle");
        }
        run.succeed(resultJson);
        auditTrail.record(feedback.getId(), run.getCycleId(), run.getId(), "runtime", "CODING_DECLARED", "SUCCESS", traceId,
                "{\"deliveryEvidence\":\"PENDING\"}");
        return run;
    }

    /** A verified provider fact is the only Coding path that may enter PR_READY. */
    @Transactional
    public void recordPrEvidenceVerified(UUID feedbackId, UUID cycleId, UUID agentRunId, String traceId) {
        Feedback feedback = get(feedbackId);
        if (!feedback.getCurrentCycleId().equals(cycleId)) {
            throw new WorkflowConflictException("Delivery evidence does not belong to the current cycle");
        }
        if (feedback.getState() == FeedbackState.PR_READY) {
            return;
        }
        if (feedback.getState() != FeedbackState.CODE_RUNNING) {
            throw new WorkflowConflictException("Feedback is not awaiting delivery evidence: " + feedback.getState());
        }
        feedback.transitionTo(FeedbackState.PR_READY);
        auditTrail.record(feedback.getId(), cycleId, agentRunId, "github", "PR_EVIDENCE_VERIFIED", "SUCCESS", traceId,
                "{\"state\":\"PR_READY\"}");
    }

    @Transactional
    public void recordPrEvidenceRejected(UUID feedbackId, UUID cycleId, UUID agentRunId, String reason, String traceId) {
        Feedback feedback = get(feedbackId);
        if (!feedback.getCurrentCycleId().equals(cycleId)) {
            throw new WorkflowConflictException("Delivery evidence does not belong to the current cycle");
        }
        if (feedback.getState() == FeedbackState.EXECUTION_FAILED) {
            return;
        }
        if (feedback.getState() != FeedbackState.CODE_RUNNING) {
            throw new WorkflowConflictException("Feedback is not awaiting delivery evidence: " + feedback.getState());
        }
        feedback.transitionTo(FeedbackState.EXECUTION_FAILED);
        auditTrail.record(feedback.getId(), cycleId, agentRunId, "github", "PR_EVIDENCE_REJECTED", "FAILED", traceId,
                "{\"reason\":\"" + reason + "\"}");
    }

    /** A signed GitHub event may enter BUILD_RUNNING only after verified evidence and an allowlisted human merge. */
    @Transactional
    public void recordAuthorizedMerge(UUID feedbackId, UUID cycleId, UUID agentRunId, String mergerLogin, String traceId) {
        Feedback feedback = get(feedbackId);
        if (!feedback.getCurrentCycleId().equals(cycleId)) {
            throw new WorkflowConflictException("Merge event does not belong to the current cycle");
        }
        if (feedback.getState() == FeedbackState.BUILD_RUNNING) {
            return;
        }
        if (feedback.getState() != FeedbackState.PR_READY) {
            throw new WorkflowConflictException("Feedback is not ready for an authorized merge: " + feedback.getState());
        }
        feedback.transitionTo(FeedbackState.BUILD_RUNNING);
        auditTrail.record(feedback.getId(), cycleId, agentRunId, "github:" + mergerLogin, "PR_MERGED", "SUCCESS", traceId,
                "{\"state\":\"BUILD_RUNNING\"}");
    }

    /** A signed, project-configured CI result advances only the current Cycle's delivery build. */
    @Transactional
    public void recordBuildEvidence(UUID feedbackId, UUID cycleId, UUID agentRunId, boolean succeeded, String traceId) {
        Feedback feedback = get(feedbackId);
        if (!feedback.getCurrentCycleId().equals(cycleId)) {
            throw new WorkflowConflictException("CI evidence does not belong to the current cycle");
        }
        boolean retried = feedback.getState() == FeedbackState.BUILD_FAILED && succeeded;
        if (retried) {
            feedback.transitionTo(FeedbackState.BUILD_RUNNING);
        }
        if (feedback.getState() != FeedbackState.BUILD_RUNNING) {
            throw new WorkflowConflictException("Feedback is not awaiting CI evidence: " + feedback.getState());
        }
        feedback.transitionTo(succeeded ? FeedbackState.DEPLOY_RUNNING : FeedbackState.BUILD_FAILED);
        auditTrail.record(feedback.getId(), cycleId, agentRunId, "github", retried ? "CI_RETRY_VERIFIED" : "CI_EVIDENCE",
                succeeded ? "SUCCESS" : "FAILED", traceId,
                "{\"state\":\"" + feedback.getState() + "\"}");
    }

    /** A signed test-environment deployment result advances only the current Cycle's delivery deployment. */
    @Transactional
    public void recordDeploymentEvidence(UUID feedbackId, UUID cycleId, UUID agentRunId, boolean succeeded, String traceId) {
        Feedback feedback = get(feedbackId);
        if (!feedback.getCurrentCycleId().equals(cycleId)) {
            throw new WorkflowConflictException("Deployment evidence does not belong to the current cycle");
        }
        boolean retried = feedback.getState() == FeedbackState.DEPLOY_FAILED && succeeded;
        if (retried) {
            feedback.transitionTo(FeedbackState.DEPLOY_RUNNING);
        }
        if (feedback.getState() != FeedbackState.DEPLOY_RUNNING) {
            throw new WorkflowConflictException("Feedback is not awaiting deployment evidence: " + feedback.getState());
        }
        feedback.transitionTo(succeeded ? FeedbackState.WAITING_VERIFY : FeedbackState.DEPLOY_FAILED);
        auditTrail.record(feedback.getId(), cycleId, agentRunId, "github",
                retried ? "DEPLOYMENT_RETRY_VERIFIED" : "DEPLOYMENT_EVIDENCE", succeeded ? "SUCCESS" : "FAILED", traceId,
                "{\"state\":\"" + feedback.getState() + "\"}");
    }

    /** A retry is a new immutable attempt in the current Cycle; terminal run facts are never overwritten. */
    @Transactional
    public Feedback retry(UUID feedbackId, AgentRole role, String actor, String traceId) {
        if (role == null) {
            throw new IllegalArgumentException("Agent role is required for retry");
        }
        Feedback feedback = get(feedbackId);
        FeedbackState expected = role == AgentRole.TRIAGE ? FeedbackState.TRIAGE_FAILED : FeedbackState.EXECUTION_FAILED;
        FeedbackState queued = role == AgentRole.TRIAGE ? FeedbackState.TRIAGE_QUEUED : FeedbackState.CODE_QUEUED;
        if (feedback.getState() != expected) {
            throw new WorkflowConflictException("Feedback is not retryable for " + role + ": " + feedback.getState());
        }
        feedback.transitionTo(queued);
        int nextAttempt = agentRuns.findMaxAttemptByCycleIdAndRole(feedback.getCurrentCycleId(), role) + 1;
        enqueueAgent(feedback, role, nextAttempt, traceId);
        auditTrail.record(feedback.getId(), feedback.getCurrentCycleId(), null, actor, "AGENT_RETRY_QUEUED", "SUCCESS", traceId,
                "{\"role\":\"" + role + "\",\"attempt\":" + nextAttempt + "}");
        return feedback;
    }

    private void queueTriage(Feedback feedback, String traceId) {
        feedback.transitionTo(FeedbackState.TRIAGE_QUEUED);
        enqueueAgent(feedback, AgentRole.TRIAGE, 1, traceId);
    }

    private void queueCoding(Feedback feedback, String traceId) {
        enqueueAgent(feedback, AgentRole.CODING, 1, traceId);
    }

    private void enqueueAgent(Feedback feedback, AgentRole role, int attempt, String traceId) {
        AgentRun run = agentRuns.save(AgentRun.queue(feedback.getId(), feedback.getCurrentCycleId(), role, attempt,
                feedback.getId() + "/" + feedback.getCurrentCycleId() + "/" + role + "/" + attempt));
        String payload = "{\"agentRunId\":\"" + run.getId() + "\"}";
        outboxEvents.save(OutboxEvent.pending("AGENT_RUN", run.getId(), "AGENT_RUN_REQUESTED", run.getIdempotencyKey(), payload));
        auditTrail.record(feedback.getId(), run.getCycleId(), run.getId(), "system", "AGENT_QUEUED", "SUCCESS", traceId,
                "{\"role\":\"" + role + "\"}");
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

    private static void validateReopenContext(String reason, String redactedContextJson, String contextSha256,
            int redactionCount) {
        if (reason == null || reason.isBlank() || redactedContextJson == null || contextSha256 == null
                || !contextSha256.matches("[a-f0-9]{64}") || redactionCount < 0) {
            throw new IllegalArgumentException("A complete sanitized reopen context is required");
        }
    }
}
