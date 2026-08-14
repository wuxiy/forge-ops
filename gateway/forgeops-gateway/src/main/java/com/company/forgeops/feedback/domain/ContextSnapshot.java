package com.company.forgeops.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** forgeops_context：Context Pack append/snapshot（§18.2）。 */
@Entity
@Table(name = "forgeops_context")
public class ContextSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feedback_id", nullable = false)
    private Long feedbackId;

    @Column(name = "schema_version", nullable = false, length = 10)
    private String schemaVersion;

    @Column(nullable = false, length = 32)
    private String reason = "INITIAL";

    @Column(name = "context_json", nullable = false, columnDefinition = "JSONB")
    private String contextJson;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }

    public Long getFeedbackId() { return feedbackId; }

    public void setFeedbackId(Long feedbackId) { this.feedbackId = feedbackId; }

    public String getSchemaVersion() { return schemaVersion; }

    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

    public String getReason() { return reason; }

    public void setReason(String reason) { this.reason = reason; }

    public String getContextJson() { return contextJson; }

    public void setContextJson(String contextJson) { this.contextJson = contextJson; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
