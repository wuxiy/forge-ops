package com.company.forgeops.v2.integration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    @Test
    void claimsThenDeliversExactlyOnce() {
        OutboxEvent event = OutboxEvent.pending("FEEDBACK", UUID.randomUUID(), "TRIAGE_REQUESTED", "key-1", "{}");
        event.beginDispatch(OffsetDateTime.now());
        event.delivered(OffsetDateTime.now());

        assertEquals(OutboxEventState.DELIVERED, event.getState());
        assertEquals(1, event.getAttempts());
        assertThrows(IllegalStateException.class, () -> event.beginDispatch(OffsetDateTime.now()));
    }

    @Test
    void failedDispatchUsesBoundedRetryAndThenManualFailure() {
        OutboxEvent event = OutboxEvent.pending("FEEDBACK", UUID.randomUUID(), "TRIAGE_REQUESTED", "key-2", "{}");
        event.beginDispatch(OffsetDateTime.now());
        event.failed("transient", 2, OffsetDateTime.now().plusSeconds(1), OffsetDateTime.now());
        assertEquals(OutboxEventState.RETRYING, event.getState());
        event.beginDispatch(OffsetDateTime.now().plusSeconds(2));
        event.failed("still failing", 2, OffsetDateTime.now().plusSeconds(3), OffsetDateTime.now());

        assertEquals(OutboxEventState.FAILED, event.getState());
        assertEquals(2, event.getAttempts());
    }
}
