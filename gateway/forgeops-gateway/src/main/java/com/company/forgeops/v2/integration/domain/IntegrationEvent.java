package com.company.forgeops.v2.integration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Durable inbox fact. It is stored before a workflow action is attempted. */
@Entity
@Table(name = "integration_event")
public class IntegrationEvent {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "external_event_id", nullable = false, length = 256)
    private String externalEventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "feedback_id", columnDefinition = "UUID")
    private UUID feedbackId;

    @Column(name = "cycle_id", columnDefinition = "UUID")
    private UUID cycleId;

    @Column(name = "agent_run_id", columnDefinition = "UUID")
    private UUID agentRunId;

    @Column(name = "payload_json", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IntegrationEventState state;

    @Column(name = "apply_attempts", nullable = false)
    private int applyAttempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "received_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime receivedAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    protected IntegrationEvent() {
    }

    public static IntegrationEvent receive(String source, String externalEventId, String eventType, String projectId,
            UUID feedbackId, UUID cycleId, UUID agentRunId, String payloadJson) {
        if (externalEventId == null || externalEventId.isBlank()) {
            throw new IllegalArgumentException("externalEventId is required");
        }
        var event = new IntegrationEvent();
        event.id = UUID.randomUUID();
        event.source = Objects.requireNonNull(source, "source");
        event.externalEventId = externalEventId;
        event.eventType = Objects.requireNonNull(eventType, "eventType");
        event.projectId = Objects.requireNonNull(projectId, "projectId");
        event.feedbackId = feedbackId;
        event.cycleId = cycleId;
        event.agentRunId = agentRunId;
        event.payloadJson = Objects.requireNonNull(payloadJson, "payloadJson");
        event.state = IntegrationEventState.RECEIVED;
        event.receivedAt = OffsetDateTime.now();
        event.updatedAt = event.receivedAt;
        return event;
    }

    public void applied() { transition(IntegrationEventState.APPLIED, null); }
    public void deferred(String reason) { transition(IntegrationEventState.DEFERRED, reason); }
    public void rejected(String reason) { transition(IntegrationEventState.REJECTED, reason); }
    public void deadLetter(String reason) { transition(IntegrationEventState.DEAD_LETTER, reason); }

    private void transition(IntegrationEventState target, String error) {
        if (state == IntegrationEventState.APPLIED || state == IntegrationEventState.REJECTED) {
            throw new IllegalStateException("A terminal event cannot be changed: " + id);
        }
        state = target;
        applyAttempts++;
        lastError = error;
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getSource() { return source; }
    public String getExternalEventId() { return externalEventId; }
    public String getEventType() { return eventType; }
    public String getProjectId() { return projectId; }
    public UUID getFeedbackId() { return feedbackId; }
    public UUID getCycleId() { return cycleId; }
    public UUID getAgentRunId() { return agentRunId; }
    public String getPayloadJson() { return payloadJson; }
    public IntegrationEventState getState() { return state; }
    public int getApplyAttempts() { return applyAttempts; }
    public String getLastError() { return lastError; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
