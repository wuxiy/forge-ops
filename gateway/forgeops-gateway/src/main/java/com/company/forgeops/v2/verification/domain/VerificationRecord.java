package com.company.forgeops.v2.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "verification_record")
public class VerificationRecord {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "feedback_id", nullable = false, columnDefinition = "UUID")
    private UUID feedbackId;

    @Column(name = "cycle_id", nullable = false, columnDefinition = "UUID")
    private UUID cycleId;

    @Column(name = "verifier_subject", nullable = false, length = 128)
    private String verifierSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VerificationResult result;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    protected VerificationRecord() {
    }

    public static VerificationRecord create(UUID feedbackId, UUID cycleId, String verifierSubject, VerificationResult result,
            String comment) {
        var record = new VerificationRecord();
        record.id = UUID.randomUUID();
        record.feedbackId = feedbackId;
        record.cycleId = cycleId;
        record.verifierSubject = verifierSubject;
        record.result = result;
        record.comment = comment;
        record.createdAt = OffsetDateTime.now();
        return record;
    }

    public UUID getId() { return id; }
    public UUID getFeedbackId() { return feedbackId; }
    public UUID getCycleId() { return cycleId; }
    public String getVerifierSubject() { return verifierSubject; }
    public VerificationResult getResult() { return result; }
    public String getComment() { return comment; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
