package com.company.forgeops.v2.verification;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.forgeops.v2.agent.domain.AgentRole;
import com.company.forgeops.v2.agent.domain.AgentRun;
import com.company.forgeops.v2.agent.domain.AgentRunRepository;
import com.company.forgeops.v2.agent.execution.AgentContracts.CodingOutcome;
import com.company.forgeops.v2.agent.execution.AgentContracts.CodingResult;
import com.company.forgeops.v2.feedback.domain.Feedback;
import com.company.forgeops.v2.feedback.domain.FeedbackRepository;
import com.company.forgeops.v2.registry.ProjectCatalog;
import com.company.forgeops.v2.registry.ResolvedProject;
import com.company.forgeops.v2.verification.domain.DeliveryEvidence;
import com.company.forgeops.v2.verification.domain.DeliveryEvidenceRepository;
import com.company.forgeops.v2.workflow.FeedbackWorkflow;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DeliveryEvidenceServiceTest {

    @Test
    void registeredProjectAndExactGitHubUrlCreateOnlyPendingEvidence() {
        UUID runId = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        AgentRun run = AgentRun.queue(feedbackId, cycleId, AgentRole.CODING, 1, "coding/1");
        FeedbackWorkflow workflow = Mockito.mock(FeedbackWorkflow.class);
        AgentRunRepository agentRuns = Mockito.mock(AgentRunRepository.class);
        FeedbackRepository feedbacks = Mockito.mock(FeedbackRepository.class);
        ProjectCatalog catalog = Mockito.mock(ProjectCatalog.class);
        DeliveryEvidenceRepository evidence = Mockito.mock(DeliveryEvidenceRepository.class);
        when(evidence.findByAgentRunId(runId)).thenReturn(Optional.empty());
        when(agentRuns.findById(runId)).thenReturn(Optional.of(run));
        when(workflow.recordCodingDeclaration(runId, "canonical", "trace")).thenReturn(run);
        Feedback feedback = feedback(feedbackId, cycleId, "pilot");
        when(feedbacks.findById(feedbackId)).thenReturn(Optional.of(feedback));
        when(catalog.require("pilot")).thenReturn(project());

        new DeliveryEvidenceService(workflow, agentRuns, feedbacks, catalog, evidence).registerCodingPrDeclaration(runId,
                declaration("https://github.com/example/pilot/pull/42"), "canonical", "trace");

        ArgumentCaptor<DeliveryEvidence> saved = ArgumentCaptor.forClass(DeliveryEvidence.class);
        verify(evidence).save(saved.capture());
        org.junit.jupiter.api.Assertions.assertEquals("example/pilot", saved.getValue().getRepository());
        org.junit.jupiter.api.Assertions.assertEquals(42, saved.getValue().getPullRequestNo());
        org.junit.jupiter.api.Assertions.assertEquals(
                com.company.forgeops.v2.verification.domain.DeliveryEvidenceState.PENDING, saved.getValue().getState());
    }

    @Test
    void foreignOrAmbiguousUrlIsRejectedBeforeTheAgentRunCanBeMarkedSuccessful() {
        UUID runId = UUID.randomUUID();
        FeedbackWorkflow workflow = Mockito.mock(FeedbackWorkflow.class);
        AgentRunRepository agentRuns = Mockito.mock(AgentRunRepository.class);
        UUID feedbackId = UUID.randomUUID();
        AgentRun run = AgentRun.queue(feedbackId, UUID.randomUUID(), AgentRole.CODING, 1, "coding/2");
        when(agentRuns.findById(runId)).thenReturn(Optional.of(run));
        FeedbackRepository feedbacks = Mockito.mock(FeedbackRepository.class);
        when(feedbacks.findById(feedbackId)).thenReturn(Optional.of(feedback(feedbackId, run.getCycleId(), "pilot")));
        ProjectCatalog catalog = Mockito.mock(ProjectCatalog.class);
        when(catalog.require("pilot")).thenReturn(project());
        DeliveryEvidenceRepository evidence = Mockito.mock(DeliveryEvidenceRepository.class);
        when(evidence.findByAgentRunId(runId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> new DeliveryEvidenceService(workflow, agentRuns, feedbacks, catalog, evidence)
                        .registerCodingPrDeclaration(runId, declaration("https://github.com/other/repo/pull/42"),
                                "canonical", "trace"));

        verify(workflow, never()).recordCodingDeclaration(any(), any(), any());
        verify(evidence, never()).save(any());
    }

    @Test
    void anAlreadyBoundGitHubPrIsRejectedBeforeItCanBeClaimedByAnotherCycle() {
        UUID runId = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();
        AgentRun run = AgentRun.queue(feedbackId, UUID.randomUUID(), AgentRole.CODING, 1, "coding/3");
        FeedbackWorkflow workflow = Mockito.mock(FeedbackWorkflow.class);
        AgentRunRepository agentRuns = Mockito.mock(AgentRunRepository.class);
        FeedbackRepository feedbacks = Mockito.mock(FeedbackRepository.class);
        ProjectCatalog catalog = Mockito.mock(ProjectCatalog.class);
        DeliveryEvidenceRepository evidence = Mockito.mock(DeliveryEvidenceRepository.class);
        when(evidence.findByAgentRunId(runId)).thenReturn(Optional.empty());
        when(agentRuns.findById(runId)).thenReturn(Optional.of(run));
        when(feedbacks.findById(feedbackId)).thenReturn(Optional.of(feedback(feedbackId, run.getCycleId(), "pilot")));
        when(catalog.require("pilot")).thenReturn(project());
        when(evidence.existsByRepositoryAndPullRequestNo("example/pilot", 42)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> new DeliveryEvidenceService(workflow, agentRuns, feedbacks, catalog, evidence)
                        .registerCodingPrDeclaration(runId, declaration("https://github.com/example/pilot/pull/42"),
                                "canonical", "trace"));

        verify(workflow, never()).recordCodingDeclaration(any(), any(), any());
        verify(evidence, never()).save(any());
    }

    private static CodingResult declaration(String url) {
        return new CodingResult(CodingOutcome.PR_CREATED, "forgeops/v2-42", "abc123", url, List.of("src/App.java"),
                List.of("mvn test"), List.of(), null, null);
    }

    private static ResolvedProject project() {
        return new ResolvedProject("pilot", Path.of("."), List.of(Path.of(".")), Set.of("https://pilot.example"),
                new ResolvedProject.GitHubDelivery("example/pilot", "main", Set.of("owner"), "forgeops-test", "test"));
    }

    private static Feedback feedback(UUID feedbackId, UUID cycleId, String projectId) {
        return Feedback.create(feedbackId, projectId, 1001, "reporter", "title", "description", cycleId);
    }
}
