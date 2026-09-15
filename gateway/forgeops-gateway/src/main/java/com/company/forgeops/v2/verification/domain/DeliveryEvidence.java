package com.company.forgeops.v2.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable claim fields plus an independently observed provider fact; no webhook or Agent body is retained. */
@Entity
@Table(name = "delivery_evidence")
public class DeliveryEvidence {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "feedback_id", nullable = false, columnDefinition = "UUID")
    private UUID feedbackId;

    @Column(name = "cycle_id", nullable = false, columnDefinition = "UUID")
    private UUID cycleId;

    @Column(name = "agent_run_id", nullable = false, columnDefinition = "UUID")
    private UUID agentRunId;

    @Column(nullable = false, length = 16)
    private String provider;

    @Column(nullable = false, length = 256)
    private String repository;

    @Column(name = "pull_request_no", nullable = false)
    private long pullRequestNo;

    @Column(name = "expected_base_branch", nullable = false, length = 256)
    private String expectedBaseBranch;

    @Column(name = "expected_head_branch", nullable = false, length = 256)
    private String expectedHeadBranch;

    @Column(name = "expected_head_sha", nullable = false, length = 128)
    private String expectedHeadSha;

    @Column(name = "expected_pr_url", nullable = false, columnDefinition = "TEXT")
    private String expectedPrUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeliveryEvidenceState state;

    @Column(name = "query_attempts", nullable = false)
    private int queryAttempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "observed_json", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String observedJson;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    protected DeliveryEvidence() {
    }

    public static DeliveryEvidence pending(UUID feedbackId, UUID cycleId, UUID agentRunId, String repository,
            long pullRequestNo, String baseBranch, String headBranch, String headSha, String prUrl) {
        var evidence = new DeliveryEvidence();
        evidence.id = UUID.randomUUID();
        evidence.feedbackId = Objects.requireNonNull(feedbackId, "feedbackId");
        evidence.cycleId = Objects.requireNonNull(cycleId, "cycleId");
        evidence.agentRunId = Objects.requireNonNull(agentRunId, "agentRunId");
        evidence.provider = "GITHUB";
        evidence.repository = require(repository, "repository");
        evidence.pullRequestNo = pullRequestNo;
        evidence.expectedBaseBranch = require(baseBranch, "baseBranch");
        evidence.expectedHeadBranch = require(headBranch, "headBranch");
        evidence.expectedHeadSha = require(headSha, "headSha");
        evidence.expectedPrUrl = require(prUrl, "prUrl");
        if (pullRequestNo < 1) {
            throw new IllegalArgumentException("pullRequestNo must be positive");
        }
        evidence.state = DeliveryEvidenceState.PENDING;
        evidence.createdAt = OffsetDateTime.now();
        evidence.updatedAt = evidence.createdAt;
        return evidence;
    }

    public void verified(String observedJson) {
        transition(DeliveryEvidenceState.VERIFIED, observedJson, null);
    }

    public void rejected(String observedJson, String reason) {
        transition(DeliveryEvidenceState.REJECTED, observedJson, require(reason, "reason"));
    }

    public void queryUnavailable(String reason) {
        if (state != DeliveryEvidenceState.PENDING) {
            return;
        }
        queryAttempts++;
        lastError = require(reason, "reason");
        updatedAt = OffsetDateTime.now();
    }

    private void transition(DeliveryEvidenceState target, String observed, String error) {
        if (state != DeliveryEvidenceState.PENDING) {
            throw new IllegalStateException("Delivery evidence is terminal: " + id);
        }
        state = target;
        queryAttempts++;
        observedJson = Objects.requireNonNull(observed, "observedJson");
        lastError = error;
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getFeedbackId() { return feedbackId; }
    public UUID getCycleId() { return cycleId; }
    public UUID getAgentRunId() { return agentRunId; }
    public String getProvider() { return provider; }
    public String getRepository() { return repository; }
    public long getPullRequestNo() { return pullRequestNo; }
    public String getExpectedBaseBranch() { return expectedBaseBranch; }
    public String getExpectedHeadBranch() { return expectedHeadBranch; }
    public String getExpectedHeadSha() { return expectedHeadSha; }
    public String getExpectedPrUrl() { return expectedPrUrl; }
    public DeliveryEvidenceState getState() { return state; }
    public int getQueryAttempts() { return queryAttempts; }
    public String getLastError() { return lastError; }
    public String getObservedJson() { return observedJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
