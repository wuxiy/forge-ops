package com.company.forgeops.v2.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** An immutable processing cycle. Reopen always creates another row. */
@Entity
@Table(name = "feedback_cycle")
public class FeedbackCycle {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "feedback_id", nullable = false, columnDefinition = "UUID")
    private UUID feedbackId;

    @Column(name = "cycle_no", nullable = false)
    private int cycleNo;

    @Column(name = "opened_by", nullable = false, length = 128)
    private String openedBy;

    @Column(name = "reopen_reason", columnDefinition = "TEXT")
    private String reopenReason;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    protected FeedbackCycle() {
    }

    public static FeedbackCycle initial(UUID feedbackId, String openedBy) {
        return create(feedbackId, 1, openedBy, null);
    }

    public static FeedbackCycle reopen(UUID feedbackId, int cycleNo, String openedBy, String reopenReason) {
        if (cycleNo < 2) {
            throw new IllegalArgumentException("Reopen cycle must start from 2");
        }
        return create(feedbackId, cycleNo, openedBy, reopenReason);
    }

    private static FeedbackCycle create(UUID feedbackId, int cycleNo, String openedBy, String reopenReason) {
        var cycle = new FeedbackCycle();
        cycle.id = UUID.randomUUID();
        cycle.feedbackId = Objects.requireNonNull(feedbackId, "feedbackId");
        cycle.cycleNo = cycleNo;
        cycle.openedBy = Objects.requireNonNull(openedBy, "openedBy");
        cycle.reopenReason = reopenReason;
        cycle.createdAt = OffsetDateTime.now();
        return cycle;
    }

    public UUID getId() { return id; }
    public UUID getFeedbackId() { return feedbackId; }
    public int getCycleNo() { return cycleNo; }
    public String getOpenedBy() { return openedBy; }
    public String getReopenReason() { return reopenReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
