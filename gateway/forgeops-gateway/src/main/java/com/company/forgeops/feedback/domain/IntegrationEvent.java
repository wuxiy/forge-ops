package com.company.forgeops.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** forgeops_integration_event（§18.5）：外部系统回调幂等 + 审计。 */
@Entity
@Table(name = "forgeops_integration_event")
public class IntegrationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feedback_id")
    private Long feedbackId;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "external_event_id", nullable = false, length = 128)
    private String externalEventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(columnDefinition = "JSONB")
    private String payload;

    @Column(nullable = false, length = 16)
    private String status = "PROCESSED";

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }

    public Long getFeedbackId() { return feedbackId; }

    public void setFeedbackId(Long feedbackId) { this.feedbackId = feedbackId; }

    public String getSource() { return source; }

    public void setSource(String source) { this.source = source; }

    public String getExternalEventId() { return externalEventId; }

    public void setExternalEventId(String externalEventId) { this.externalEventId = externalEventId; }

    public String getEventType() { return eventType; }

    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }

    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
