package com.company.forgeops.v2.integration.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.forgeops.v2.integration.domain.IntegrationEvent;
import com.company.forgeops.v2.integration.inbox.IntegrationApplier;
import com.company.forgeops.v2.registry.ProjectCatalog;
import com.company.forgeops.v2.registry.ResolvedProject;
import com.company.forgeops.v2.verification.domain.DeliveryEvidence;
import com.company.forgeops.v2.verification.domain.DeliveryEvidenceRepository;
import com.company.forgeops.v2.verification.domain.DeliveryEvidenceState;
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
        when(catalog.resolveGitHubRepository("example/pilot")).thenReturn(Optional.of(project()));
        when(evidence.findByRepositoryAndPullRequestNoAndState("example/pilot", 42, DeliveryEvidenceState.VERIFIED))
                .thenReturn(List.of(item));

        IntegrationApplier.ApplyResult result = applier(evidence, catalog, workflow).apply(event("owner", false, "abc123"));

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
        when(catalog.resolveGitHubRepository("example/pilot")).thenReturn(Optional.of(project()));
        when(evidence.findByRepositoryAndPullRequestNoAndState("example/pilot", 42, DeliveryEvidenceState.VERIFIED))
                .thenReturn(List.of(item));
        GitHubIntegrationApplier applier = applier(evidence, catalog, workflow);

        assertEquals(IntegrationApplier.Disposition.REJECTED, applier.apply(event("intruder", false, "abc123")).disposition());
        assertEquals(IntegrationApplier.Disposition.DEFERRED, applier.apply(event("owner", false, "wrong-sha")).disposition());
        verify(workflow, never()).recordAuthorizedMerge(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static GitHubIntegrationApplier applier(DeliveryEvidenceRepository evidence, ProjectCatalog catalog,
            FeedbackWorkflow workflow) {
        return new GitHubIntegrationApplier(evidence, catalog, workflow, JsonMapper.shared());
    }

    private static IntegrationEvent event(String merger, boolean draft, String sha) throws Exception {
        return IntegrationEvent.receive("GITHUB", "delivery-42", "PULL_REQUEST", "pilot", null, null, null,
                payload(merger, draft, sha));
    }

    private static String payload(String merger, boolean draft, String sha) throws Exception {
        var result = new LinkedHashMap<String, Object>();
        result.put("payloadSha256", "a".repeat(64));
        result.put("repository", "example/pilot");
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

    private static DeliveryEvidence verifiedEvidence() {
        DeliveryEvidence item = DeliveryEvidence.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "example/pilot", 42, "main", "forgeops/v2-42", "abc123", "https://github.com/example/pilot/pull/42");
        item.verified("{}");
        return item;
    }

    private static ResolvedProject project() {
        return new ResolvedProject("pilot", Path.of("."), List.of(Path.of(".")), Set.of("https://pilot.example"),
                new ResolvedProject.GitHubDelivery("example/pilot", "main", Set.of("owner"), "test"));
    }
}
