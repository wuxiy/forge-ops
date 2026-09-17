package com.company.forgeops.v2.agent.domain;

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

/** One immutable attempt for one role in one Cycle. A retry creates another row. */
@Entity
@Table(name = "agent_run")
public class AgentRun {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "feedback_id", nullable = false, columnDefinition = "UUID")
    private UUID feedbackId;

    @Column(name = "cycle_id", nullable = false, columnDefinition = "UUID")
    private UUID cycleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentRole role;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "idempotency_key", nullable = false, length = 256)
    private String idempotencyKey;

    @Column(name = "provider_run_id", length = 128)
    private String providerRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentRunState state;

    @Column(name = "failure_category", length = 64)
    private String failureCategory;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    @Column(name = "result_json", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String resultJson;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    protected AgentRun() {
    }

    public static AgentRun queue(UUID feedbackId, UUID cycleId, AgentRole role, int attempt, String idempotencyKey) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        var run = new AgentRun();
        run.id = UUID.randomUUID();
        run.feedbackId = Objects.requireNonNull(feedbackId, "feedbackId");
        run.cycleId = Objects.requireNonNull(cycleId, "cycleId");
        run.role = Objects.requireNonNull(role, "role");
        run.attempt = attempt;
        run.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        run.state = AgentRunState.QUEUED;
        run.createdAt = OffsetDateTime.now();
        run.updatedAt = run.createdAt;
        return run;
    }

    public void markRunning(String providerRunId) {
        transition(AgentRunState.QUEUED, AgentRunState.RUNNING);
        this.providerRunId = Objects.requireNonNull(providerRunId, "providerRunId");
    }

    public void succeed(String resultJson) {
        transition(AgentRunState.RUNNING, AgentRunState.SUCCEEDED);
        this.resultJson = Objects.requireNonNull(resultJson, "resultJson");
    }

    public void fail(AgentRunState terminalState, String category, String message) {
        if (terminalState != AgentRunState.FAILED && terminalState != AgentRunState.INVALID_OUTPUT
                && terminalState != AgentRunState.CANCELLED && terminalState != AgentRunState.TIMED_OUT) {
            throw new IllegalArgumentException("terminalState must be a failure state");
        }
        if (state != AgentRunState.QUEUED && state != AgentRunState.RUNNING) {
            throw new IllegalStateException("Run is not active: " + state);
        }
        state = terminalState;
        failureCategory = category;
        failureMessage = message;
        updatedAt = OffsetDateTime.now();
    }

    private void transition(AgentRunState from, AgentRunState target) {
        if (state != from) {
            throw new IllegalStateException("Run must be " + from + " but was " + state);
        }
        state = target;
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getFeedbackId() { return feedbackId; }
    public UUID getCycleId() { return cycleId; }
    public AgentRole getRole() { return role; }
    public int getAttempt() { return attempt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getProviderRunId() { return providerRunId; }
    public AgentRunState getState() { return state; }
    public String getFailureCategory() { return failureCategory; }
    public String getFailureMessage() { return failureMessage; }
    public String getResultJson() { return resultJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
