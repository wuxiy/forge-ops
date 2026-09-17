package com.company.forgeops.v2.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only workflow audit record. Details must already be redacted by the caller. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "feedback_id", columnDefinition = "UUID")
    private UUID feedbackId;

    @Column(name = "cycle_id", columnDefinition = "UUID")
    private UUID cycleId;

    @Column(name = "agent_run_id", columnDefinition = "UUID")
    private UUID agentRunId;

    @Column(nullable = false, length = 128)
    private String actor;

    @Column(nullable = false, length = 96)
    private String action;

    @Column(nullable = false, length = 16)
    private String outcome;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "detail_json", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String detailJson;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    protected AuditLog() {
    }

    public static AuditLog record(UUID feedbackId, UUID cycleId, UUID agentRunId, String actor, String action,
            String outcome, String traceId, String detailJson) {
        var log = new AuditLog();
        log.id = UUID.randomUUID();
        log.feedbackId = feedbackId;
        log.cycleId = cycleId;
        log.agentRunId = agentRunId;
        log.actor = actor;
        log.action = action;
        log.outcome = outcome;
        log.traceId = traceId;
        log.detailJson = detailJson;
        log.createdAt = OffsetDateTime.now();
        return log;
    }

    public UUID getId() { return id; }
    public UUID getFeedbackId() { return feedbackId; }
    public UUID getCycleId() { return cycleId; }
    public UUID getAgentRunId() { return agentRunId; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getOutcome() { return outcome; }
    public String getTraceId() { return traceId; }
    public String getDetailJson() { return detailJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
