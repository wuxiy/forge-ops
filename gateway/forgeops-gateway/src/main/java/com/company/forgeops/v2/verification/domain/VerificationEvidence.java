package com.company.forgeops.v2.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A sanitized, digest-bound verification fact. Big artifacts stay on disk with a hash reference (ADR-0010). */
@Entity
@Table(name = "verification_evidence")
public class VerificationEvidence {

    public enum Kind { CHECK_RUN, EXECUTOR_ARTIFACT, SCHEDULED_RUN, GATE_NOTE }

    /** PR_CHECK and EXECUTOR and SCHEDULED may gate; POST_MERGE_CHECK may never gate a PR (VER-03). */
    public enum Source { PR_CHECK, POST_MERGE_CHECK, EXECUTOR, SCHEDULED }

    public enum Conclusion { SUCCESS, FAILURE, NEUTRAL }

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "plan_id", nullable = false, columnDefinition = "UUID")
    private UUID planId;

    @Column(name = "run_id", columnDefinition = "UUID")
    private UUID runId;

    @Column(nullable = false, length = 24)
    private String kind;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(length = 64)
    private String category;

    @Column(name = "head_sha", nullable = false, length = 128)
    private String headSha;

    @Column(nullable = false, length = 16)
    private String conclusion;

    @Column(name = "payload_digest", nullable = false, length = 64)
    private String payloadDigest;

    @Column(name = "redacted_payload", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String redactedPayload;

    @Column(name = "artifact_path", columnDefinition = "TEXT")
    private String artifactPath;

    @Column(name = "artifact_sha256", length = 64)
    private String artifactSha256;

    @Column(name = "collected_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime collectedAt;

    @Column(name = "expires_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime expiresAt;

    @Column(name = "purged_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime purgedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    protected VerificationEvidence() {
    }

    public static VerificationEvidence record(UUID planId, UUID runId, Kind kind, Source source, String category,
            String headSha, Conclusion conclusion, String payloadDigest, String redactedPayloadJson,
            OffsetDateTime expiresAt) {
        var evidence = new VerificationEvidence();
        evidence.id = UUID.randomUUID();
        evidence.planId = Objects.requireNonNull(planId, "planId");
        evidence.runId = runId;
        evidence.kind = Objects.requireNonNull(kind, "kind").name();
        evidence.source = Objects.requireNonNull(source, "source").name();
        evidence.category = category;
        evidence.headSha = Objects.requireNonNull(headSha, "headSha");
        evidence.conclusion = Objects.requireNonNull(conclusion, "conclusion").name();
        evidence.payloadDigest = Objects.requireNonNull(payloadDigest, "payloadDigest");
        evidence.redactedPayload = Objects.requireNonNull(redactedPayloadJson, "redactedPayload");
        evidence.collectedAt = OffsetDateTime.now();
        evidence.expiresAt = expiresAt;
        evidence.createdAt = evidence.collectedAt;
        return evidence;
    }

    /** Retention cleanup keeps the digest row and marks the payload purged; the action itself is audited (ADR-0010). */
    public void purge() {
        if (purgedAt != null) {
            return;
        }
        redactedPayload = "{\"purged\":true}";
        artifactPath = null;
        artifactSha256 = null;
        purgedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getPlanId() { return planId; }
    public UUID getRunId() { return runId; }
    public String getKind() { return kind; }
    public String getSource() { return source; }
    public String getCategory() { return category; }
    public String getHeadSha() { return headSha; }
    public String getConclusion() { return conclusion; }
    public String getPayloadDigest() { return payloadDigest; }
    public String getRedactedPayload() { return redactedPayload; }
    public String getArtifactPath() { return artifactPath; }
    public String getArtifactSha256() { return artifactSha256; }
    public OffsetDateTime getCollectedAt() { return collectedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getPurgedAt() { return purgedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
