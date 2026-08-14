package com.company.forgeops.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** forgeops_feedback（架构文档 §18.1）。 */
@Entity
@Table(name = "forgeops_feedback")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "forgeops_feedback_seq")
    @SequenceGenerator(name = "forgeops_feedback_seq", sequenceName = "forgeops_feedback_seq", allocationSize = 1)
    private Long id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "expected_behavior", columnDefinition = "TEXT")
    private String expectedBehavior;

    @Column(columnDefinition = "TEXT")
    private String steps;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "reporter_id", length = 64)
    private String reporterId;

    @Column(name = "reporter_name", nullable = false, length = 64)
    private String reporterName;

    @Column(nullable = false, length = 20)
    private String environment;

    @Column(name = "page_url", length = 512)
    private String pageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FeedbackStatus status;

    @Column(name = "frontend_version", length = 64)
    private String frontendVersion;

    @Column(name = "frontend_commit", length = 64)
    private String frontendCommit;

    @Column(name = "backend_version", length = 64)
    private String backendVersion;

    @Column(name = "backend_commit", length = 64)
    private String backendCommit;

    @Column(name = "multica_issue_id", length = 64)
    private String multicaIssueId;

    @Column(name = "multica_issue_url", length = 256)
    private String multicaIssueUrl;

    @Column(name = "pr_url", length = 512)
    private String prUrl;

    @Column(name = "ci_pipeline_id", length = 64)
    private String ciPipelineId;

    @Column(name = "deployment_version", length = 64)
    private String deploymentVersion;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    /** 对外标识：FB-1023。 */
    public String identifier() {
        return "FB-" + id;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void transitionTo(FeedbackStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "非法状态迁移: " + status + " -> " + target + " (FB-" + id + ")");
        }
        this.status = target;
    }

    // --- getters / setters ---

    public Long getId() { return id; }

    public String getProjectId() { return projectId; }

    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getType() { return type; }

    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public String getExpectedBehavior() { return expectedBehavior; }

    public void setExpectedBehavior(String expectedBehavior) { this.expectedBehavior = expectedBehavior; }

    public String getSteps() { return steps; }

    public void setSteps(String steps) { this.steps = steps; }

    public String getNote() { return note; }

    public void setNote(String note) { this.note = note; }

    public String getReporterId() { return reporterId; }

    public void setReporterId(String reporterId) { this.reporterId = reporterId; }

    public String getReporterName() { return reporterName; }

    public void setReporterName(String reporterName) { this.reporterName = reporterName; }

    public String getEnvironment() { return environment; }

    public void setEnvironment(String environment) { this.environment = environment; }

    public String getPageUrl() { return pageUrl; }

    public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }

    public FeedbackStatus getStatus() { return status; }

    public void setStatus(FeedbackStatus status) { this.status = status; }

    public String getFrontendVersion() { return frontendVersion; }

    public void setFrontendVersion(String frontendVersion) { this.frontendVersion = frontendVersion; }

    public String getFrontendCommit() { return frontendCommit; }

    public void setFrontendCommit(String frontendCommit) { this.frontendCommit = frontendCommit; }

    public String getBackendVersion() { return backendVersion; }

    public void setBackendVersion(String backendVersion) { this.backendVersion = backendVersion; }

    public String getBackendCommit() { return backendCommit; }

    public void setBackendCommit(String backendCommit) { this.backendCommit = backendCommit; }

    public String getMulticaIssueId() { return multicaIssueId; }

    public void setMulticaIssueId(String multicaIssueId) { this.multicaIssueId = multicaIssueId; }

    public String getMulticaIssueUrl() { return multicaIssueUrl; }

    public void setMulticaIssueUrl(String multicaIssueUrl) { this.multicaIssueUrl = multicaIssueUrl; }

    public String getPrUrl() { return prUrl; }

    public void setPrUrl(String prUrl) { this.prUrl = prUrl; }

    public String getCiPipelineId() { return ciPipelineId; }

    public void setCiPipelineId(String ciPipelineId) { this.ciPipelineId = ciPipelineId; }

    public String getDeploymentVersion() { return deploymentVersion; }

    public void setDeploymentVersion(String deploymentVersion) { this.deploymentVersion = deploymentVersion; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
