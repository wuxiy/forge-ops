package com.company.forgeops.v2.integration.domain;

public enum OutboxEventState {
    PENDING,
    DISPATCHING,
    DELIVERED,
    RETRYING,
    FAILED
}
