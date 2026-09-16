package com.company.forgeops.v2.integration.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.forgeops.v2.feedback.domain.Feedback;
import com.company.forgeops.v2.feedback.domain.FeedbackRepository;
import com.company.forgeops.v2.integration.domain.IntegrationEvent;
import com.company.forgeops.v2.integration.inbox.IntegrationApplier;
import com.company.forgeops.v2.registry.ProjectCatalog;
import com.company.forgeops.v2.registry.ResolvedProject;
import com.company.forgeops.v2.verification.domain.DeliveryEvidence;
import com.company.forgeops.v2.verification.domain.DeliveryEvidenceRepository;
import com.company.forgeops.v2.verification.domain.DeliveryEvidenceState;
import com.company.forgeops.v2.workflow.FeedbackState;
import com.company.forgeops.v2.workflow.FeedbackWorkflow;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

class GitHubIntegrationApplierTest {

    @Test
    void verifiedEvidenceAndAllowlistedMergeFactEnterBuildRunning() throws Exception {
        DeliveryEvidence item = verifiedEvidence();
        ProjectCatalog catalog = Mockito.mock(ProjectCatalog.class);
        DeliveryEvidenceRepository evidence = Mockito.mock(DeliveryEvidenceRepository.class);
        FeedbackWorkflow workflow = Mockito.mock(FeedbackWorkflow.class);
        FeedbackRepository feedbacks = Mockito.mock(FeedbackRepository.class);
        Feedback feedback = currentFeedback(item, FeedbackState.GATE_PASS);
        when(feedbacks.findById(item.getFeedbackId())).thenReturn(Optional.of(feedback));
        when(catalog.resolveGitHubRepository("example/pilot")).thenReturn(Optional.of(project()));
        when(evidence.findByRepositoryAndPullRequestNoAndState("example/pilot", 42, DeliveryEvidenceState.VERIFIED))
                .thenReturn(List.of(item));

        IntegrationApplier.ApplyResult result = applier(evidence, catalog, workflow, feedbacks)
                .apply(pullRequestEvent("owner", false, "abc123"));

        assertEquals(IntegrationApplier.Disposition.APPLIED, result.disposition());
        verify(workflow).recordAuthorizedMerge(eq(item.getFeedbackId()), eq(item.getCycleId()), eq(item.getAgentRunId()),
                eq("owner"), eq("github:delivery-42"));
    }

    @Test
    void unauthorizedActorIsRejectedAndWrongCommitStaysDeferredForDiagnosis() throws Exception {
        DeliveryEvidence item = verifiedEvidence();
        ProjectCatalog catalog = Mockito.mock(ProjectCatalog.class);
        DeliveryEvidenceRepository evidence = Mockito.mock(DeliveryEvidenceRepository.class);
        FeedbackWorkflow workflow = Mockito.mock(FeedbackWorkflow.class);
        FeedbackRepository feedbacks = Mockito.mock(FeedbackRepository.class);
        Feedback feedback = currentFeedback(item, FeedbackState.GATE_PASS);
        when(feedbacks.findById(item.getFeedbackId())).thenReturn(Optional.of(feedback));
        when(catalog.resolveGitHubRepository("example/pilot")).thenReturn(Optional.of(project()));
        when(evidence.findByRepositoryAndPullRequestNoAndState("example/pilot", 42, DeliveryEvidenceState.VERIFIED))
                .thenReturn(List.of(item));
        GitHubIntegrationApplier applier = applier(evidence, catalog, workflow, feedbacks);

        assertEquals(IntegrationApplier.Disposition.REJECTED,
                applier.apply(pullRequestEvent("intruder", false, "abc123")).disposition());
        assertEquals(IntegrationApplier.Disposition.DEFERRED,
                applier.apply(pullRequestEvent("owner", false, "wrong-sha")).disposition());
        verify(workflow, never()).recordAuthorizedMerge(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void configuredCheckAndExactPrHeadAdvanceBuildWhileOtherChecksCannot() throws Exception {
        DeliveryEvidence item = verifiedEvidence();
        ProjectCatalog catalog = Mockito.mock(ProjectCatalog.class);
        DeliveryEvidenceRepository evidence = Mockito.mock(DeliveryEvidenceRepository.class);
        FeedbackWorkflow workflow = Mockito.mock(FeedbackWorkflow.class);
        FeedbackRepository feedbacks = Mockito.mock(FeedbackRepository.class);
        Feedback feedback = currentFeedback(item, FeedbackState.BUILD_RUNNING);
        when(catalog.resolveGitHubRepository("example/pilot")).thenReturn(Optional.of(project()));
        when(evidence.findByRepositoryAndPullRequestNoAndState("example/pilot", 42, DeliveryEvidenceState.VERIFIED))
                .thenReturn(List.of(item));
        when(feedbacks.findById(item.getFeedbackId())).thenReturn(Optional.of(feedback));
        GitHubIntegrationApplier applier = applier(evidence, catalog, workflow, feedbacks);

        assertEquals(IntegrationApplier.Disposition.REJECTED,
                applier.apply(checkRunEvent("unrelated-check", "abc123", "success")).disposition());
        assertEquals(IntegrationApplier.Disposition.APPLIED,
                applier.apply(checkRunEvent("forgeops-test", "abc123", "success")).disposition());
        verify(workflow).recordBuildEvidence(item.getFeedbackId(), item.getCycleId(), item.getAgentRunId(), true,
                "github:check-42");
    }

    @Test
    void deploymentFailureAndAReRunSuccessUseOnlyTheConfiguredTestEnvironment() throws Exception {
        DeliveryEvidence item = verifiedEvidence();
        ProjectCatalog catalog = Mockito.mock(ProjectCatalog.class);
        DeliveryEvidenceRepository evidence = Mockito.mock(DeliveryEvidenceRepository.class);
        FeedbackWorkflow workflow = Mockito.mock(FeedbackWorkflow.class);
        FeedbackRepository feedbacks = Mockito.mock(FeedbackRepository.class);
        Feedback feedback = currentFeedback(item, FeedbackState.DEPLOY_RUNNING);
        when(catalog.resolveGitHubRepository("example/pilot")).thenReturn(Optional.of(project()));
        when(evidence.findByRepositoryAndPullRequestNoAndState("example/pilot", 42, DeliveryEvidenceState.VERIFIED))
                .thenReturn(List.of(item));
        when(feedbacks.findById(item.getFeedbackId())).thenReturn(Optional.of(feedback));
        GitHubIntegrationApplier applier = applier(evidence, catalog, workflow, feedbacks);

        assertEquals(IntegrationApplier.Disposition.REJECTED,
                applier.apply(deploymentEvent("production", "success")).disposition());
        assertEquals(IntegrationApplier.Disposition.APPLIED, applier.apply(deploymentEvent("test", "failure")).disposition());
        verify(workflow).recordDeploymentEvidence(item.getFeedbackId(), item.getCycleId(), item.getAgentRunId(), false,
                "github:deployment-42");
    }

    private static GitHubIntegrationApplier applier(DeliveryEvidenceRepository evidence, ProjectCatalog catalog,
            FeedbackWorkflow workflow, FeedbackRepository feedbacks) {
        return new GitHubIntegrationApplier(evidence, catalog, workflow, feedbacks,
                Mockito.mock(com.company.forgeops.v2.verification.VerificationService.class),
                new GitHubWebhookProperties(), JsonMapper.shared());
    }

    private static IntegrationEvent pullRequestEvent(String merger, boolean draft, String sha) throws Exception {
        return IntegrationEvent.receive("GITHUB", "delivery-42", "PULL_REQUEST", "pilot", null, null, null,
                pullRequestPayload(merger, draft, sha));
    }

    private static IntegrationEvent checkRunEvent(String checkName, String sha, String conclusion) throws Exception {
        return IntegrationEvent.receive("GITHUB", "check-42", "CHECK_RUN", "pilot", null, null, null,
                checkRunPayload(checkName, sha, conclusion));
    }

    private static IntegrationEvent deploymentEvent(String environment, String state) throws Exception {
        return IntegrationEvent.receive("GITHUB", "deployment-42", "DEPLOYMENT_STATUS", "pilot", null, null, null,
                deploymentPayload(environment, state));
    }

    private static String pullRequestPayload(String merger, boolean draft, String sha) throws Exception {
        var result = basePayload();
        result.put("action", "closed");
        result.put("number", 42);
        result.put("baseBranch", "main");
        result.put("headBranch", "forgeops/v2-42");
        result.put("headSha", sha);
        result.put("draft", draft);
        result.put("merged", true);
        result.put("mergedBy", merger);
        return JsonMapper.shared().writeValueAsString(result);
    }

    private static String checkRunPayload(String checkName, String sha, String conclusion) throws Exception {
        var result = basePayload();
        result.put("action", "completed");
        result.put("checkRunId", 77);
        result.put("checkRunName", checkName);
        result.put("pullRequestNo", 42);
        result.put("headSha", sha);
        result.put("status", "completed");
        result.put("conclusion", conclusion);
        return JsonMapper.shared().writeValueAsString(result);
    }

    private static String deploymentPayload(String environment, String state) throws Exception {
        var result = basePayload();
        result.put("action", "created");
        result.put("deploymentId", 9);
        result.put("deploymentStatusId", 10);
        result.put("pullRequestNo", 42);
        result.put("headSha", "abc123");
        result.put("environment", environment);
        result.put("state", state);
        return JsonMapper.shared().writeValueAsString(result);
    }

    private static LinkedHashMap<String, Object> basePayload() {
        var result = new LinkedHashMap<String, Object>();
        result.put("payloadSha256", "a".repeat(64));
        result.put("repository", "example/pilot");
        return result;
    }

    private static DeliveryEvidence verifiedEvidence() {
        DeliveryEvidence item = DeliveryEvidence.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "example/pilot", 42, "main", "forgeops/v2-42", "abc123", "https://github.com/example/pilot/pull/42");
        item.verified("{}");
        return item;
    }

    private static Feedback currentFeedback(DeliveryEvidence item, FeedbackState state) {
        Feedback feedback = Mockito.mock(Feedback.class);
        when(feedback.getCurrentCycleId()).thenReturn(item.getCycleId());
        when(feedback.getState()).thenReturn(state);
        return feedback;
    }

    private static ResolvedProject project() {
        return new ResolvedProject("pilot", Path.of("."), List.of(Path.of(".")), Set.of("https://pilot.example"),
                new ResolvedProject.GitHubDelivery("example/pilot", "main", Set.of("owner"), "forgeops-test", "test"),
                ResolvedProject.defaultPolicy());
    }
}
