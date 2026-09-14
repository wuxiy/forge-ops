package com.company.forgeops.v2.feedback.domain;

import com.company.forgeops.v2.workflow.FeedbackState;
import com.company.forgeops.v2.workflow.WorkflowConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Stable feedback identity. Mutable workflow state points only to the active Cycle. */
@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "display_no", nullable = false)
    private long displayNo;

    @Column(name = "reporter_subject", nullable = false, length = 128)
    private String reporterSubject;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "current_cycle_id", nullable = false, columnDefinition = "UUID")
    private UUID currentCycleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FeedbackState state;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    protected Feedback() {
    }

    public static Feedback create(UUID id, String projectId, long displayNo, String reporterSubject, String title,
            String description, UUID cycleId) {
        var feedback = new Feedback();
        feedback.id = Objects.requireNonNull(id, "id");
        feedback.projectId = require(projectId, "projectId");
        feedback.displayNo = displayNo;
        feedback.reporterSubject = require(reporterSubject, "reporterSubject");
        feedback.title = require(title, "title");
        feedback.description = require(description, "description");
        feedback.currentCycleId = Objects.requireNonNull(cycleId, "cycleId");
        feedback.state = FeedbackState.RECEIVED;
        feedback.createdAt = OffsetDateTime.now();
        feedback.updatedAt = feedback.createdAt;
        return feedback;
    }

    public void transitionTo(FeedbackState target) {
        if (!state.canTransitionTo(target)) {
            throw new WorkflowConflictException("Illegal transition: " + state + " -> " + target + " for feedback " + id);
        }
        state = target;
        updatedAt = OffsetDateTime.now();
    }

    public void moveToCycle(UUID cycleId) {
        currentCycleId = Objects.requireNonNull(cycleId, "cycleId");
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getProjectId() { return projectId; }
    public long getDisplayNo() { return displayNo; }
    public String getReporterSubject() { return reporterSubject; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public UUID getCurrentCycleId() { return currentCycleId; }
    public FeedbackState getState() { return state; }
    public long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
