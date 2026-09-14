package com.company.forgeops.v2.integration.inbox;

import java.util.UUID;

/** Validated transport data. Signature validation is added by the Phase 3 access boundary. */
public record InboundEvent(
        String source,
        String externalEventId,
        String eventType,
        String projectId,
        UUID feedbackId,
        UUID cycleId,
        UUID agentRunId,
        String payloadJson,
        String traceId) {
}
