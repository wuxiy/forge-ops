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

/** One verification plan for one PR head of one Cycle. A re-push expires the old plan (ADR-0002). */
@Entity
@Table(name = "verification_plan")
public class VerificationPlan {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "feedback_id", nullable = false, columnDefinition = "UUID")
    private UUID feedbackId;

    @Column(name = "cycle_id", nullable = false, columnDefinition = "UUID")
    private UUID cycleId;

    @Column(name = "agent_run_id", columnDefinition = "UUID")
    private UUID agentRunId;

    @Column(name = "delivery_evidence_id", nullable = false, columnDefinition = "UUID")
    private UUID deliveryEvidenceId;

    @Column(name = "pr_head_sha", nullable = false, length = 128)
    private String prHeadSha;

    @Column(name = "pr_url", nullable = false, columnDefinition = "TEXT")
    private String prUrl;

    /** PLANNER = Paseo verification role; FALLBACK = deterministic plan (ADR-0011). */
    @Column(nullable = false, length = 16)
    private String origin;

    @Column(name = "fallback_reason", length = 64)
    private String fallbackReason;

    @Column(name = "selection_mode", nullable = false, length = 16)
    private String selectionMode;

    @Column(name = "required_categories", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String requiredCategories;

    @Column(name = "selected_categories", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String selectedCategories;

    @Column(name = "impact_files", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String impactFiles;

    @Column(name = "risk_level", length = 16)
    private String riskLevel;

    @Column(name = "graph_baseline_commit", length = 128)
    private String graphBaselineCommit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private VerificationPlanStatus status;

    @Column(name = "gate_decision", length = 8)
    private String gateDecision;

    @Column(name = "gate_reason", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String gateReason;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    protected VerificationPlan() {
    }

    public static VerificationPlan create(UUID feedbackId, UUID cycleId, UUID deliveryEvidenceId, String prHeadSha,
            String prUrl, String origin, String fallbackReason, String selectionMode, String requiredCategoriesJson,
            String selectedCategoriesJson, String impactFilesJson, String graphBaselineCommit) {
        var plan = new VerificationPlan();
        plan.id = UUID.randomUUID();
        plan.feedbackId = Objects.requireNonNull(feedbackId, "feedbackId");
        plan.cycleId = Objects.requireNonNull(cycleId, "cycleId");
        plan.deliveryEvidenceId = Objects.requireNonNull(deliveryEvidenceId, "deliveryEvidenceId");
        plan.prHeadSha = Objects.requireNonNull(prHeadSha, "prHeadSha");
        plan.prUrl = Objects.requireNonNull(prUrl, "prUrl");
        plan.origin = Objects.requireNonNull(origin, "origin");
        plan.fallbackReason = fallbackReason;
        plan.selectionMode = Objects.requireNonNull(selectionMode, "selectionMode");
        plan.requiredCategories = Objects.requireNonNull(requiredCategoriesJson, "requiredCategories");
        plan.selectedCategories = Objects.requireNonNull(selectedCategoriesJson, "selectedCategories");
        plan.impactFiles = Objects.requireNonNull(impactFilesJson, "impactFiles");
        plan.graphBaselineCommit = graphBaselineCommit;
        plan.status = VerificationPlanStatus.PLANNED;
        plan.createdAt = OffsetDateTime.now();
        plan.updatedAt = plan.createdAt;
        return plan;
    }

    public void attachPlannerRun(UUID agentRunId) {
        this.agentRunId = Objects.requireNonNull(agentRunId, "agentRunId");
        touch();
    }

    /** ADR-0011: the deterministic fallback takes over the same plan; the reason is audited by the caller. */
    public void markFallback(String reason) {
        if (status != VerificationPlanStatus.PLANNED) {
            throw new IllegalStateException("Only a planned verification can fall back: " + status);
        }
        origin = "FALLBACK";
        fallbackReason = Objects.requireNonNull(reason, "reason");
        agentRunId = null;
        touch();
    }

    public void activate(String requiredCategoriesJson, String selectedCategoriesJson, String impactFilesJson,
            String riskLevel, String graphBaselineCommit) {
        transition(VerificationPlanStatus.PLANNED, VerificationPlanStatus.RUNNING);
        this.requiredCategories = Objects.requireNonNull(requiredCategoriesJson);
        this.selectedCategories = Objects.requireNonNull(selectedCategoriesJson);
        this.impactFiles = Objects.requireNonNull(impactFilesJson);
        this.riskLevel = riskLevel;
        this.graphBaselineCommit = graphBaselineCommit;
        touch();
    }

    public void complete(String gateDecision, String gateReasonJson) {
        transition(VerificationPlanStatus.RUNNING, VerificationPlanStatus.COMPLETED);
        this.gateDecision = Objects.requireNonNull(gateDecision);
        this.gateReason = gateReasonJson;
        touch();
    }

    public void expire() {
        if (status != VerificationPlanStatus.PLANNED && status != VerificationPlanStatus.RUNNING) {
            throw new IllegalStateException("Only an open plan can expire: " + status);
        }
        status = VerificationPlanStatus.EXPIRED;
        touch();
    }

    private void transition(VerificationPlanStatus from, VerificationPlanStatus target) {
        if (status != from) {
            throw new IllegalStateException("Plan must be " + from + " but was " + status);
        }
        status = target;
    }

    private void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getFeedbackId() { return feedbackId; }
    public UUID getCycleId() { return cycleId; }
    public UUID getAgentRunId() { return agentRunId; }
    public UUID getDeliveryEvidenceId() { return deliveryEvidenceId; }
    public String getPrHeadSha() { return prHeadSha; }
    public String getPrUrl() { return prUrl; }
    public String getOrigin() { return origin; }
    public String getFallbackReason() { return fallbackReason; }
    public String getSelectionMode() { return selectionMode; }
    public String getRequiredCategories() { return requiredCategories; }
    public String getSelectedCategories() { return selectedCategories; }
    public String getImpactFiles() { return impactFiles; }
    public String getRiskLevel() { return riskLevel; }
    public String getGraphBaselineCommit() { return graphBaselineCommit; }
    public VerificationPlanStatus getStatus() { return status; }
    public String getGateDecision() { return gateDecision; }
    public String getGateReason() { return gateReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
