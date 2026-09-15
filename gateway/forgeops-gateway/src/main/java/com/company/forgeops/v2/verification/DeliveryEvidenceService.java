package com.company.forgeops.v2.verification;

import com.company.forgeops.v2.agent.domain.AgentRun;
import com.company.forgeops.v2.agent.domain.AgentRunRepository;
import com.company.forgeops.v2.agent.execution.AgentContracts.CodingResult;
import com.company.forgeops.v2.feedback.domain.Feedback;
import com.company.forgeops.v2.feedback.domain.FeedbackRepository;
import com.company.forgeops.v2.registry.ProjectCatalog;
import com.company.forgeops.v2.registry.ResolvedProject;
import com.company.forgeops.v2.verification.domain.DeliveryEvidence;
import com.company.forgeops.v2.verification.domain.DeliveryEvidenceRepository;
import com.company.forgeops.v2.workflow.FeedbackWorkflow;
import java.net.URI;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registers a Coding PR declaration as pending evidence; it performs no provider query and no state advancement. */
@Service
public class DeliveryEvidenceService {

    private final FeedbackWorkflow workflow;
    private final AgentRunRepository agentRuns;
    private final FeedbackRepository feedbacks;
    private final ProjectCatalog catalog;
    private final DeliveryEvidenceRepository evidence;

    public DeliveryEvidenceService(FeedbackWorkflow workflow, AgentRunRepository agentRuns, FeedbackRepository feedbacks,
            ProjectCatalog catalog, DeliveryEvidenceRepository evidence) {
        this.workflow = workflow;
        this.agentRuns = agentRuns;
        this.feedbacks = feedbacks;
        this.catalog = catalog;
        this.evidence = evidence;
    }

    @Transactional
    public void registerCodingPrDeclaration(UUID agentRunId, CodingResult declaration, String canonicalResultJson,
            String traceId) {
        if (declaration == null || declaration.prUrl() == null || declaration.branch() == null || declaration.commitSha() == null) {
            throw new IllegalArgumentException("A complete Coding PR declaration is required");
        }
        if (evidence.findByAgentRunId(agentRunId).isPresent()) {
            return;
        }
        AgentRun pendingRun = agentRuns.findById(agentRunId)
                .orElseThrow(() -> new IllegalArgumentException("Agent run does not exist: " + agentRunId));
        Feedback feedback = feedbacks.findById(pendingRun.getFeedbackId())
                .orElseThrow(() -> new IllegalArgumentException("Feedback does not exist: " + pendingRun.getFeedbackId()));
        ResolvedProject project = catalog.require(feedback.getProjectId());
        long pullRequestNo = parsePublicGitHubPullUrl(project.github().repository(), declaration.prUrl());
        if (evidence.existsByRepositoryAndPullRequestNo(project.github().repository(), pullRequestNo)) {
            throw new IllegalArgumentException("GitHub pull request is already bound to another delivery cycle");
        }
        AgentRun run = workflow.recordCodingDeclaration(agentRunId, canonicalResultJson, traceId);
        if (!run.getId().equals(pendingRun.getId()) || !run.getCycleId().equals(pendingRun.getCycleId())) {
            throw new IllegalStateException("Coding run changed while its delivery declaration was registered");
        }
        evidence.save(DeliveryEvidence.pending(feedback.getId(), run.getCycleId(), run.getId(), project.github().repository(),
                pullRequestNo, project.github().baseBranch(), declaration.branch(), declaration.commitSha(), declaration.prUrl()));
    }

    private static long parsePublicGitHubPullUrl(String repository, String prUrl) {
        URI uri;
        try {
            uri = URI.create(prUrl);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Coding PR URL is invalid", invalid);
        }
        String expectedPrefix = "/" + repository + "/pull/";
        if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost()) || uri.getRawQuery() != null
                || uri.getRawFragment() != null || uri.getPath() == null || !uri.getPath().startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("Coding PR URL is outside the registered GitHub repository");
        }
        String number = uri.getPath().substring(expectedPrefix.length());
        if (!number.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("Coding PR URL must contain a positive pull request number");
        }
        try {
            return Long.parseLong(number);
        } catch (NumberFormatException outOfRange) {
            throw new IllegalArgumentException("Coding PR number is out of range", outOfRange);
        }
    }
}
