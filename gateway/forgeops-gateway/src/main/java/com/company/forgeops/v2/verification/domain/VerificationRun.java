package com.company.forgeops.v2.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** One executable verification attempt for one category of one plan (PR check, executor or scheduled). */
@Entity
@Table(name = "verification_run")
public class VerificationRun {

    public enum Status { PENDING, RUNNING, PASSED, FAILED, SKIPPED }

    public enum Source { PR_CHECK, EXECUTOR, SCHEDULED }

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "plan_id", nullable = false, columnDefinition = "UUID")
    private UUID planId;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(nullable = false, length = 16)
    private String source;

    @Column(name = "head_sha", nullable = false, length = 128)
    private String headSha;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "external_ref", length = 256)
    private String externalRef;

    @Column(name = "failure_category", length = 64)
    private String failureCategory;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    @Column(name = "seed_digest", length = 64)
    private String seedDigest;

    @Column(name = "started_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime finishedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    protected VerificationRun() {
    }

    public static VerificationRun pending(UUID planId, String category, Source source, String headSha, int attempt,
            String seedDigest) {
        var run = new VerificationRun();
        run.id = UUID.randomUUID();
        run.planId = Objects.requireNonNull(planId, "planId");
        run.category = Objects.requireNonNull(category, "category");
        run.source = Objects.requireNonNull(source, "source").name();
        run.headSha = Objects.requireNonNull(headSha, "headSha");
        run.status = Status.PENDING.name();
        run.attempt = Math.max(1, attempt);
        run.seedDigest = seedDigest;
        run.createdAt = OffsetDateTime.now();
        run.updatedAt = run.createdAt;
        return run;
    }

    /** An explicit retry is a new attempt with an audit trail; re-running to green is not a PASS (ADR-0008). */
    public boolean isCurrentAttempt() {
        return status != null && !Status.valueOf(status).equals(Status.SKIPPED);
    }

    public void markRunning(String externalRef) {
        transition(Status.PENDING, Status.RUNNING);
        this.externalRef = externalRef;
        startedAt = OffsetDateTime.now();
        touch();
    }

    public void markPassed() {
        transition(Status.RUNNING, Status.PASSED);
        finishedAt = OffsetDateTime.now();
        touch();
    }

    public void markFailed(String failureCategory, String failureMessage) {
        transition(Status.RUNNING, Status.FAILED);
        this.failureCategory = failureCategory;
        this.failureMessage = failureMessage;
        finishedAt = OffsetDateTime.now();
        touch();
    }

    public void markSkipped() {
        if (Status.valueOf(status) != Status.PENDING) {
            throw new IllegalStateException("Only a pending verification run can be skipped: " + status);
        }
        status = Status.SKIPPED.name();
        finishedAt = OffsetDateTime.now();
        touch();
    }

    private void transition(Status from, Status target) {
        if (Status.valueOf(status) != from) {
            throw new IllegalStateException("Verification run must be " + from + " but was " + status);
        }
        status = target.name();
    }

    private void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getPlanId() { return planId; }
    public String getCategory() { return category; }
    public String getSource() { return source; }
    public String getHeadSha() { return headSha; }
    public String getStatus() { return status; }
    public int getAttempt() { return attempt; }
    public String getExternalRef() { return externalRef; }
    public String getFailureCategory() { return failureCategory; }
    public String getFailureMessage() { return failureMessage; }
    public String getSeedDigest() { return seedDigest; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
