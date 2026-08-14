package com.company.forgeops.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** forgeops_feedback_verification（§18.4）。 */
@Entity
@Table(name = "forgeops_feedback_verification")
public class VerificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feedback_id", nullable = false)
    private Long feedbackId;

    @Column(name = "verifier_id", length = 64)
    private String verifierId;

    @Column(name = "verifier_name", nullable = false, length = 64)
    private String verifierName;

    @Column(nullable = false, length = 10)
    private String result;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }

    public Long getFeedbackId() { return feedbackId; }

    public void setFeedbackId(Long feedbackId) { this.feedbackId = feedbackId; }

    public String getVerifierId() { return verifierId; }

    public void setVerifierId(String verifierId) { this.verifierId = verifierId; }

    public String getVerifierName() { return verifierName; }

    public void setVerifierName(String verifierName) { this.verifierName = verifierName; }

    public String getResult() { return result; }

    public void setResult(String result) { this.result = result; }

    public String getComment() { return comment; }

    public void setComment(String comment) { this.comment = comment; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
