package com.company.forgeops.v2.integration.outbox;

import com.company.forgeops.v2.integration.domain.OutboxEvent;
import com.company.forgeops.v2.integration.domain.OutboxEventRepository;
import com.company.forgeops.v2.integration.domain.OutboxEventState;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Claims an event in one database transaction, calls the external publisher outside it, then
 * records the result in another transaction. This is deliberately not a scheduled provider workflow.
 */
@Service
public class OutboxDispatcher {

    private static final int MAX_ATTEMPTS = 5;

    private final OutboxEventRepository events;
    private final OutboxPublisher publisher;
    private final TransactionTemplate transactions;

    public OutboxDispatcher(OutboxEventRepository events, OutboxPublisher publisher, TransactionTemplate transactions) {
        this.events = events;
        this.publisher = publisher;
        this.transactions = transactions;
    }

    public int dispatchDue() {
        List<OutboxMessage> claimed = transactions.execute(status -> claimDue(OffsetDateTime.now()));
        if (claimed == null) {
            return 0;
        }
        claimed.forEach(this::deliver);
        return claimed.size();
    }

    /** Dispatches one known outbox fact, used by controlled replay and fault-injection tests. */
    public boolean dispatch(java.util.UUID eventId) {
        OutboxMessage claimed = transactions.execute(status -> events.lockById(eventId)
                .filter(event -> event.getState() == OutboxEventState.PENDING || event.getState() == OutboxEventState.RETRYING)
                .filter(event -> !event.getNextAttemptAt().isAfter(OffsetDateTime.now()))
                .map(event -> claim(event, OffsetDateTime.now()))
                .orElse(null));
        if (claimed == null) {
            return false;
        }
        deliver(claimed);
        return true;
    }

    public int recoverAbandonedDispatches(Duration olderThan) {
        return transactions.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            List<OutboxEvent> abandoned = events.findByStateAndUpdatedAtBefore(OutboxEventState.DISPATCHING,
                    now.minus(olderThan));
            abandoned.forEach(event -> event.recoverAbandonedDispatch(now));
            return abandoned.size();
        });
    }

    private List<OutboxMessage> claimDue(OffsetDateTime now) {
        return events.lockDue(EnumSet.of(OutboxEventState.PENDING, OutboxEventState.RETRYING), now).stream()
                .limit(50)
                .map(event -> claim(event, now))
                .toList();
    }

    private OutboxMessage claim(OutboxEvent event, OffsetDateTime now) {
        event.beginDispatch(now);
        return new OutboxMessage(event.getId(), event.getEventType(), event.getIdempotencyKey(), event.getPayloadJson());
    }

    private void markDelivered(java.util.UUID eventId) {
        events.findById(eventId).orElseThrow().delivered(OffsetDateTime.now());
    }

    private void deliver(OutboxMessage message) {
        try {
            publisher.publish(message);
            transactions.executeWithoutResult(status -> markDelivered(message.eventId()));
        } catch (RuntimeException error) {
            transactions.executeWithoutResult(status -> markFailed(message.eventId(), error));
        }
    }

    private void markFailed(java.util.UUID eventId, RuntimeException error) {
        OffsetDateTime now = OffsetDateTime.now();
        OutboxEvent event = events.findById(eventId).orElseThrow();
        long seconds = Math.min(300, 1L << Math.min(8, event.getAttempts()));
        event.failed(safeMessage(error), MAX_ATTEMPTS, now.plusSeconds(seconds), now);
    }

    private static String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message.substring(0, Math.min(512, message.length()));
    }
}
