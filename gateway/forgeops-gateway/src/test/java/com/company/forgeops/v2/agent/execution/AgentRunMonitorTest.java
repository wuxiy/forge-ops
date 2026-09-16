package com.company.forgeops.v2.agent.execution;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.forgeops.v2.agent.domain.AgentRole;
import com.company.forgeops.v2.agent.domain.AgentRun;
import com.company.forgeops.v2.agent.domain.AgentRunRepository;
import com.company.forgeops.v2.agent.domain.AgentRunState;
import com.company.forgeops.v2.workflow.FeedbackWorkflow;
import com.company.forgeops.v2.verification.DeliveryEvidenceService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

class AgentRunMonitorTest {

    @Test
    void aValidButUnverifiedCodingPrDeclarationOnlyRegistersPendingEvidence() {
        AgentRun run = AgentRun.queue(UUID.randomUUID(), UUID.randomUUID(), AgentRole.CODING, 1, "coding-run-1");
        run.markRunning("paseo-coding-1");
        AgentRunRepository runs = Mockito.mock(AgentRunRepository.class);
        AgentExecution execution = Mockito.mock(AgentExecution.class);
        FeedbackWorkflow workflow = Mockito.mock(FeedbackWorkflow.class);
        DeliveryEvidenceService deliveryEvidence = Mockito.mock(DeliveryEvidenceService.class);
        when(runs.findByState(AgentRunState.RUNNING)).thenReturn(List.of(run));
        when(execution.inspect("coding-run-1")).thenReturn(new RuntimeRunSnapshot("coding-run-1", "paseo-coding-1",
                AgentRunState.SUCCEEDED, null,
                "{\"outcome\":\"PR_CREATED\",\"branch\":\"forgeops/v2-fix\",\"commitSha\":\"abc123\",\"prUrl\":\"https://example.invalid/pr/1\",\"changedFiles\":[\"src/App.java\"],\"tests\":[\"mvn test\"],\"risks\":[],\"failureCategory\":null,\"failureMessage\":null}", null));

        new AgentRunMonitor(runs, execution, workflow, deliveryEvidence,
                Mockito.mock(com.company.forgeops.v2.verification.VerificationService.class),
                JsonMapper.shared()).inspectRunning();

        verify(deliveryEvidence).registerCodingPrDeclaration(eq(run.getId()),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                startsWith("monitor:"));
    }
}
