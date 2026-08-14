package com.company.forgeops.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** forgeops_audit_log：全链路审计（§19.3）。 */
@Entity
@Table(name = "forgeops_audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feedback_id")
    private Long feedbackId;

    @Column(nullable = false, length = 64)
    private String actor;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(columnDefinition = "JSONB")
    private String detail;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }

    public Long getFeedbackId() { return feedbackId; }

    public void setFeedbackId(Long feedbackId) { this.feedbackId = feedbackId; }

    public String getActor() { return actor; }

    public void setActor(String actor) { this.actor = actor; }

    public String getAction() { return action; }

    public void setAction(String action) { this.action = action; }

    public String getDetail() { return detail; }

    public void setDetail(String detail) { this.detail = detail; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
