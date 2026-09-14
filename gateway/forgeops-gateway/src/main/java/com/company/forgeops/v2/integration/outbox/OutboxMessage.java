package com.company.forgeops.v2.integration.outbox;

import java.util.UUID;

/** A provider-agnostic external command. The idempotency key must be honoured by every publisher. */
public record OutboxMessage(UUID eventId, String eventType, String idempotencyKey, String payloadJson) {
}
