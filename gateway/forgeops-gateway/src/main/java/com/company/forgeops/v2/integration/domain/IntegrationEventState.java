package com.company.forgeops.v2.integration.domain;

public enum IntegrationEventState {
    RECEIVED,
    APPLIED,
    DEFERRED,
    REJECTED,
    DEAD_LETTER
}
