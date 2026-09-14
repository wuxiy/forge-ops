package com.company.forgeops.v2.agent.execution;

import com.company.forgeops.v2.agent.domain.AgentRole;
import java.nio.file.Path;
import java.util.Map;

/** Already-redacted, server-side command sent from the Outbox worker to the private Runtime. */
public record RuntimeRunRequest(String idempotencyKey, String projectId, AgentRole role, Path cwd, String prompt,
        Map<String, Object> outputSchema) {
}
