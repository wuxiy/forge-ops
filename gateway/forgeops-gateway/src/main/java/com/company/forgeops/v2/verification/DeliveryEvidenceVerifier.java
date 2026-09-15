package com.company.forgeops.v2.verification;

import com.company.forgeops.v2.integration.github.GitHubProviderUnavailableException;
import com.company.forgeops.v2.integration.github.GitHubPullRequestClient;
import com.company.forgeops.v2.integration.github.GitHubWebhookProperties;
import com.company.forgeops.v2.feedback.domain.FeedbackRepository;
import com.company.forgeops.v2.verification.domain.DeliveryEvidence;
import com.company.forgeops.v2.verification.domain.DeliveryEvidenceRepository;
import com.company.forgeops.v2.verification.domain.DeliveryEvidenceState;
import com.company.forgeops.v2.workflow.FeedbackWorkflow;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Reconciles pending Coding delivery facts outside the Agent and webhook paths. */
@Service
public class DeliveryEvidenceVerifier {

    private final DeliveryEvidenceRepository evidence;
    private final GitHubWebhookProperties github;
    private final GitHubPullRequestClient pullRequests;
    private final GitHubPullRequestEvidenceMatcher matcher;
    private final FeedbackWorkflow workflow;
    private final FeedbackRepository feedbacks;
    private final TransactionTemplate transactions;

    public DeliveryEvidenceVerifier(DeliveryEvidenceRepository evidence, GitHubWebhookProperties github,
            GitHubPullRequestClient pullRequests, GitHubPullRequestEvidenceMatcher matcher, FeedbackWorkflow workflow,
            FeedbackRepository feedbacks, TransactionTemplate transactions) {
        this.evidence = evidence;
        this.github = github;
        this.pullRequests = pullRequests;
        this.matcher = matcher;
        this.workflow = workflow;
        this.feedbacks = feedbacks;
        this.transactions = transactions;
    }

    public int reconcilePending() {
        if (!github.isEnabled()) {
            return 0;
        }
        List<UUID> pending = transactions.execute(status -> evidence.findByState(DeliveryEvidenceState.PENDING).stream()
                .map(DeliveryEvidence::getId).toList());
        if (pending == null) {
            return 0;
        }
        pending.forEach(this::verify);
        return pending.size();
    }

    private void verify(UUID evidenceId) {
        DeliveryEvidence expected = transactions.execute(status -> evidence.findById(evidenceId)
                .filter(item -> item.getState() == DeliveryEvidenceState.PENDING).orElse(null));
        if (expected == null) {
            return;
        }
        if (!awaitingCurrentCodingRun(expected)) {
            transactions.executeWithoutResult(status -> evidence.findById(evidenceId)
                    .filter(item -> item.getState() == DeliveryEvidenceState.PENDING)
                    .ifPresent(item -> item.rejected("{\"reason\":\"EVIDENCE_NOT_CURRENT_CODE_RUN\"}",
                            "EVIDENCE_NOT_CURRENT_CODE_RUN")));
            return;
        }
        try {
            var observed = pullRequests.getPullRequest(expected.getRepository(), expected.getPullRequestNo());
            var result = matcher.match(expected, observed);
            transactions.executeWithoutResult(status -> apply(expected.getId(), result));
        } catch (GitHubProviderUnavailableException unavailable) {
            transactions.executeWithoutResult(status -> evidence.findById(evidenceId)
                    .filter(item -> item.getState() == DeliveryEvidenceState.PENDING)
                    .ifPresent(item -> item.queryUnavailable("GITHUB_QUERY_UNAVAILABLE")));
        }
    }

    private boolean awaitingCurrentCodingRun(DeliveryEvidence expected) {
        return feedbacks.findById(expected.getFeedbackId())
                .map(feedback -> feedback.getCurrentCycleId().equals(expected.getCycleId())
                        && feedback.getState() == com.company.forgeops.v2.workflow.FeedbackState.CODE_RUNNING)
                .orElse(false);
    }

    private void apply(UUID evidenceId, GitHubPullRequestEvidenceMatcher.MatchResult result) {
        DeliveryEvidence managed = evidence.findById(evidenceId).orElseThrow();
        if (managed.getState() != DeliveryEvidenceState.PENDING) {
            return;
        }
        String traceId = "github:evidence:" + managed.getId();
        if (result.matches()) {
            managed.verified(result.observedJson());
            workflow.recordPrEvidenceVerified(managed.getFeedbackId(), managed.getCycleId(), managed.getAgentRunId(), traceId);
            return;
        }
        String reason = "MISMATCH_" + String.join("_", result.mismatches());
        managed.rejected(result.observedJson(), reason);
        workflow.recordPrEvidenceRejected(managed.getFeedbackId(), managed.getCycleId(), managed.getAgentRunId(), reason, traceId);
    }
}
