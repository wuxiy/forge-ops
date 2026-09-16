package com.company.forgeops.v2.registry;

import java.util.List;

/** Global gate-policy defaults; every project resolves against these and may only tighten (ADR-0006). */
public record GlobalQualityPolicy(List<String> requiredCategories, int defaultEvidenceRetentionDays,
        int maxEvidenceRetentionDays, int graphMaxAgeDays, int graphMaxMergeLag, int recallGuardRuns) {

    public static GlobalQualityPolicy defaults() {
        return new GlobalQualityPolicy(List.of("BUILD"), 30, 90, 7, 50, 20);
    }

    public GlobalQualityPolicy {
        requiredCategories = List.copyOf(requiredCategories);
        if (requiredCategories.isEmpty() || requiredCategories.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("global requiredCategories must be a non-empty list of names");
        }
        if (defaultEvidenceRetentionDays < 1 || defaultEvidenceRetentionDays > maxEvidenceRetentionDays) {
            throw new IllegalArgumentException("evidence retention must be between 1 and the configured maximum");
        }
        if (graphMaxAgeDays < 1 || graphMaxMergeLag < 1 || recallGuardRuns < 1) {
            throw new IllegalArgumentException("graph and recall guard limits must be positive");
        }
    }
}
