package com.company.forgeops.v2.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable, already-redacted context bound to exactly one Cycle. */
@Entity
@Table(name = "context_snapshot")
public class ContextSnapshot {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "feedback_id", nullable = false, columnDefinition = "UUID")
    private UUID feedbackId;

    @Column(name = "cycle_id", nullable = false, columnDefinition = "UUID")
    private UUID cycleId;

    @Column(name = "schema_version", nullable = false, length = 32)
    private String schemaVersion;

    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @Column(name = "redaction_count", nullable = false)
    private int redactionCount;

    @Column(name = "content_json", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String contentJson;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    protected ContextSnapshot() {
    }

    public static ContextSnapshot create(UUID feedbackId, UUID cycleId, String schemaVersion, String contentSha256,
            int redactionCount, String contentJson) {
        var snapshot = new ContextSnapshot();
        snapshot.id = UUID.randomUUID();
        snapshot.feedbackId = Objects.requireNonNull(feedbackId, "feedbackId");
        snapshot.cycleId = Objects.requireNonNull(cycleId, "cycleId");
        snapshot.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        snapshot.contentSha256 = Objects.requireNonNull(contentSha256, "contentSha256");
        snapshot.redactionCount = redactionCount;
        snapshot.contentJson = Objects.requireNonNull(contentJson, "contentJson");
        snapshot.createdAt = OffsetDateTime.now();
        return snapshot;
    }

    public UUID getId() { return id; }
    public UUID getFeedbackId() { return feedbackId; }
    public UUID getCycleId() { return cycleId; }
    public String getSchemaVersion() { return schemaVersion; }
    public String getContentSha256() { return contentSha256; }
    public int getRedactionCount() { return redactionCount; }
    public String getContentJson() { return contentJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
