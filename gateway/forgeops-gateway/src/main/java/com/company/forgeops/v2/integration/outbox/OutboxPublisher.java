package com.company.forgeops.v2.integration.outbox;

public interface OutboxPublisher {
    void publish(OutboxMessage message);
}
