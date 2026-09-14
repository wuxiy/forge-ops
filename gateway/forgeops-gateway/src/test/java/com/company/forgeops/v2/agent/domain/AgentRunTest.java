package com.company.forgeops.v2.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentRunTest {

    @Test
    void anAttemptHasAnImmutableIdentityAndExplicitTerminalResult() {
        AgentRun run = AgentRun.queue(UUID.randomUUID(), UUID.randomUUID(), AgentRole.TRIAGE, 1, "f/c/triage/1");
        run.markRunning("provider-1");
        run.fail(AgentRunState.INVALID_OUTPUT, "INVALID_OUTPUT", "schema missing decision");

        assertEquals(AgentRunState.INVALID_OUTPUT, run.getState());
        assertEquals("provider-1", run.getProviderRunId());
        assertEquals("INVALID_OUTPUT", run.getFailureCategory());
        assertThrows(IllegalStateException.class, () -> run.succeed("{}"));
    }
}
