package com.company.forgeops.v2.agent.execution;

import com.company.forgeops.v2.agent.domain.AgentRunState;

/** Provider-neutral Runtime fact. resultJson is only returned over the service-authenticated internal boundary. */
public record RuntimeRunSnapshot(String idempotencyKey, String providerRunId, AgentRunState state,
        String failureCategory, String resultJson, String resultError) {
}
