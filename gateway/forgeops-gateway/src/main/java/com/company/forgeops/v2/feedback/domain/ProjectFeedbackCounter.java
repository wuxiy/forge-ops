package com.company.forgeops.v2.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_feedback_counter")
public class ProjectFeedbackCounter {

    @Id
    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(name = "last_value", nullable = false)
    private long lastValue;

    protected ProjectFeedbackCounter() {
    }

    public static ProjectFeedbackCounter start(String projectId) {
        var counter = new ProjectFeedbackCounter();
        counter.projectId = projectId;
        counter.lastValue = 1000;
        return counter;
    }

    public long next() {
        return ++lastValue;
    }

    public String getProjectId() { return projectId; }
    public long getLastValue() { return lastValue; }
}
