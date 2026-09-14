package com.company.forgeops.v2.integration.outbox;

import org.springframework.stereotype.Component;

/** Fail closed until a concrete internal Runtime publisher is installed in Phase 5. */
@Component
public class UnavailableOutboxPublisher implements OutboxPublisher {

    @Override
    public void publish(OutboxMessage message) {
        throw new IllegalStateException("No internal outbox publisher is configured for " + message.eventType());
    }
}
