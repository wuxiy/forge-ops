package com.company.forgeops.v2.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** ADR-0009: per-project selection state including the recall circuit breaker. */
@Entity
@Table(name = "verification_selection_state")
public class VerificationSelectionState {

    @Id
    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(name = "subtractive_enabled", nullable = false)
    private boolean subtractiveEnabled;

    @Column(name = "consecutive_full_recall_runs", nullable = false)
    private int consecutiveFullRecallRuns;

    @Column(name = "breaker_open", nullable = false)
    private boolean breakerOpen;

    @Column(name = "breaker_reason", columnDefinition = "TEXT")
    private String breakerReason;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    protected VerificationSelectionState() {
    }

    public static VerificationSelectionState fresh(String projectId) {
        var state = new VerificationSelectionState();
        state.projectId = projectId;
        state.updatedAt = OffsetDateTime.now();
        return state;
    }

    /** One missed regression opens the breaker; re-enable requires the recorded Owner review (ADR-0009). */
    public void openBreaker(String reason) {
        breakerOpen = true;
        breakerReason = reason;
        subtractiveEnabled = false;
        updatedAt = OffsetDateTime.now();
    }

    /** The recorded Owner review may clear the breaker; the recall guard still applies before enabling. */
    public void clearBreakerAfterOwnerReview() {
        breakerOpen = false;
        updatedAt = OffsetDateTime.now();
    }

    public void recordFullRecallOutcome(boolean recalledAll) {
        consecutiveFullRecallRuns = recalledAll ? consecutiveFullRecallRuns + 1 : 0;
        updatedAt = OffsetDateTime.now();
    }

    public boolean tryEnableSubtractive(int requiredConsecutiveRuns) {
        if (breakerOpen || consecutiveFullRecallRuns < requiredConsecutiveRuns) {
            return false;
        }
        subtractiveEnabled = true;
        updatedAt = OffsetDateTime.now();
        return true;
    }

    public String getProjectId() { return projectId; }
    public boolean isSubtractiveEnabled() { return subtractiveEnabled; }
    public int getConsecutiveFullRecallRuns() { return consecutiveFullRecallRuns; }
    public boolean isBreakerOpen() { return breakerOpen; }
    public String getBreakerReason() { return breakerReason; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
