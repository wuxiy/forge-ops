package com.company.forgeops.cicd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.forgeops.cicd.callback.CallbackService;
import com.company.forgeops.feedback.domain.IntegrationEvent;
import com.company.forgeops.feedback.domain.IntegrationEventRepository;
import com.company.forgeops.policy.agent.HumanGate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CallbackIdempotencyTest {

    @Test
    void duplicateExternalEventIsProcessedOnce() {
        IntegrationEventRepository repo = mock(IntegrationEventRepository.class);
        // 第一次查不到 -> 处理并保存；第二次查到 -> 返回 DUPLICATED
        when(repo.findBySourceAndExternalEventId(eq("GIT"), eq("evt-001")))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new IntegrationEvent()));
        CallbackService service = new CallbackService(repo, null, null, null, new HumanGate(), null);

        CallbackService.CallbackPayload payload = new CallbackService.CallbackPayload(
                "demo-app", null, "evt-001", "wuxi", "USER", "PING", null, null, null, null, null, null);

        assertEquals("PROCESSED", service.handle("GIT", payload));
        assertEquals("DUPLICATED", service.handle("GIT", payload));

        ArgumentCaptor<IntegrationEvent> captor = ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(repo, times(1)).save(captor.capture());
        assertEquals("GIT", captor.getValue().getSource());
        assertEquals("evt-001", captor.getValue().getExternalEventId());
    }

    @Test
    void agentMergeIsRejectedByHumanGate() {
        HumanGate gate = new HumanGate();
        assertThrows(IllegalArgumentException.class, () -> gate.assertMergeByHuman("AGENT"));
        gate.assertMergeByHuman("USER");
        assertEquals(false, gate.agentCanMerge());
        assertEquals(false, gate.agentCanDeploy());
    }

    @Test
    void gitCallbackWithAgentMergeActorIsRejected() {
        IntegrationEventRepository repo = mock(IntegrationEventRepository.class);
        when(repo.findBySourceAndExternalEventId(any(), any())).thenReturn(Optional.empty());
        CallbackService service = new CallbackService(repo, null, null, null, new HumanGate(), null);

        CallbackService.CallbackPayload payload = new CallbackService.CallbackPayload(
                "demo-app", null, "evt-002", "forgeops-coding", "AGENT", "PR_MERGED", null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.handle("GIT", payload));
    }
}
