package com.company.forgeops.v2.integration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Transactional outbox entry. Dispatch and retry behavior is implemented in Phase 2. */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, columnDefinition = "UUID")
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 256)
    private String idempotencyKey;

    @Column(name = "payload_json", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxEventState state;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected OutboxEvent() {
    }

    public static OutboxEvent pending(String aggregateType, UUID aggregateId, String eventType, String idempotencyKey,
            String payloadJson) {
        var event = new OutboxEvent();
        event.id = UUID.randomUUID();
        event.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType");
        event.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
        event.eventType = Objects.requireNonNull(eventType, "eventType");
        event.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        event.payloadJson = Objects.requireNonNull(payloadJson, "payloadJson");
        event.state = OutboxEventState.PENDING;
        event.nextAttemptAt = OffsetDateTime.now();
        event.createdAt = event.nextAttemptAt;
        event.updatedAt = event.nextAttemptAt;
        return event;
    }

    public void beginDispatch(OffsetDateTime now) {
        if (state != OutboxEventState.PENDING && state != OutboxEventState.RETRYING) {
            throw new IllegalStateException("Outbox event is not dispatchable: " + state);
        }
        if (nextAttemptAt.isAfter(now)) {
            throw new IllegalStateException("Outbox event is not due yet");
        }
        state = OutboxEventState.DISPATCHING;
        attempts++;
        updatedAt = now;
    }

    public void delivered(OffsetDateTime now) {
        if (state != OutboxEventState.DISPATCHING) {
            throw new IllegalStateException("Only a dispatching event can be delivered");
        }
        state = OutboxEventState.DELIVERED;
        lastError = null;
        updatedAt = now;
    }

    public void failed(String error, int maximumAttempts, OffsetDateTime nextAttempt, OffsetDateTime now) {
        if (state != OutboxEventState.DISPATCHING) {
            throw new IllegalStateException("Only a dispatching event can fail");
        }
        lastError = error;
        if (attempts >= maximumAttempts) {
            state = OutboxEventState.FAILED;
        } else {
            state = OutboxEventState.RETRYING;
            nextAttemptAt = nextAttempt;
        }
        updatedAt = now;
    }

    public void recoverAbandonedDispatch(OffsetDateTime now) {
        if (state != OutboxEventState.DISPATCHING) {
            return;
        }
        state = OutboxEventState.RETRYING;
        nextAttemptAt = now;
        lastError = "dispatch lease expired";
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPayloadJson() { return payloadJson; }
    public OutboxEventState getState() { return state; }
    public int getAttempts() { return attempts; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
