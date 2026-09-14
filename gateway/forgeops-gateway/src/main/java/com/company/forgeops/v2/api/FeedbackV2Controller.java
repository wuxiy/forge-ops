package com.company.forgeops.v2.api;

import com.company.forgeops.v2.audit.AuditTrail;
import com.company.forgeops.v2.context.ContextPreparation;
import com.company.forgeops.v2.context.PreparedContext;
import com.company.forgeops.v2.feedback.domain.Feedback;
import com.company.forgeops.v2.feedback.domain.FeedbackRepository;
import com.company.forgeops.v2.security.CallerIdentity;
import com.company.forgeops.v2.security.ProjectToken;
import com.company.forgeops.v2.workflow.FeedbackWorkflow;
import com.company.forgeops.v2.workflow.SubmitFeedbackCommand;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Minimal v2 browser surface. It deliberately exposes no merge, deploy, or runtime-control operation. */
@RestController
@RequestMapping("/api/v2/projects/{projectId}/feedback")
public class FeedbackV2Controller {

    private final FeedbackWorkflow workflow;
    private final FeedbackRepository feedbacks;
    private final ContextPreparation contextPreparation;
    private final AuditTrail auditTrail;

    public FeedbackV2Controller(FeedbackWorkflow workflow, FeedbackRepository feedbacks,
            ContextPreparation contextPreparation, AuditTrail auditTrail) {
        this.workflow = workflow;
        this.feedbacks = feedbacks;
        this.contextPreparation = contextPreparation;
        this.auditTrail = auditTrail;
    }

    @PostMapping
    public ResponseEntity<FeedbackView> submit(@PathVariable String projectId, @RequestBody SubmitRequest request) {
        ProjectToken identity = authorize(projectId, "feedback:write", null);
        PreparedContext context = contextPreparation.prepare(request.title(), request.description(), request.browserContext());
        Feedback feedback = workflow.submit(new SubmitFeedbackCommand(projectId, identity.subject(), context.title(),
                context.description(), context.contentJson(), context.sha256(), context.redactionCount(), request.traceId()));
        return ResponseEntity.created(URI.create("/api/v2/projects/" + projectId + "/feedback/" + feedback.getId()))
                .body(FeedbackView.from(feedback));
    }

    @GetMapping("/mine")
    public List<FeedbackView> mine(@PathVariable String projectId) {
        ProjectToken identity = authorize(projectId, "feedback:read", null);
        return feedbacks.findByProjectIdAndReporterSubjectOrderByCreatedAtDesc(projectId, identity.subject()).stream()
                .map(FeedbackView::from).toList();
    }

    @GetMapping("/{feedbackId}")
    public FeedbackView get(@PathVariable String projectId, @PathVariable UUID feedbackId) {
        ProjectToken identity = authorize(projectId, "feedback:read", feedbackId);
        Feedback feedback = feedbacks.findByIdAndProjectId(feedbackId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireOwner(identity, feedback);
        return FeedbackView.from(feedback);
    }

    @PostMapping("/{feedbackId}/reopen")
    public FeedbackView reopen(@PathVariable String projectId, @PathVariable UUID feedbackId, @RequestBody ReopenRequest request) {
        ProjectToken identity = authorize(projectId, "feedback:reopen", feedbackId);
        Feedback feedback = feedbacks.findByIdAndProjectId(feedbackId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireOwner(identity, feedback);
        PreparedContext context = contextPreparation.prepare("reopen", request.reason(), Map.of());
        return FeedbackView.from(workflow.reopen(feedbackId, identity.subject(), context.description(), context.contentJson(),
                context.sha256(), context.redactionCount(), request.traceId()));
    }

    private ProjectToken authorize(String projectId, String scope, UUID feedbackId) {
        ProjectToken identity = CallerIdentity.require();
        if (!identity.projectId().equals(projectId) || !identity.allows(scope)) {
            auditTrail.record(feedbackId, null, null, identity.subject(), "AUTHORIZATION_DENIED", "REJECTED", null,
                    "{\"projectId\":\"" + projectId + "\",\"scope\":\"" + scope + "\"}");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return identity;
    }

    private void requireOwner(ProjectToken identity, Feedback feedback) {
        if (!identity.subject().equals(feedback.getReporterSubject())) {
            auditTrail.record(feedback.getId(), feedback.getCurrentCycleId(), null, identity.subject(), "AUTHORIZATION_DENIED",
                    "REJECTED", null, "{\"reason\":\"not_owner\"}");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    public record SubmitRequest(String title, String description, Map<String, String> browserContext, String traceId) {
    }

    public record ReopenRequest(String reason, String traceId) {
    }

    public record FeedbackView(UUID id, long displayNo, String state) {
        static FeedbackView from(Feedback feedback) {
            return new FeedbackView(feedback.getId(), feedback.getDisplayNo(), feedback.getState().userVisibleStatus());
        }
    }
}
