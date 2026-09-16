package com.company.forgeops.v2.registry;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, validated project policy used by all v2 callers. */
public record ResolvedProject(String id, Path repositoryRoot, List<Path> allowedPaths, Set<String> browserOrigins,
        GitHubDelivery github, QualityPolicy qualityPolicy) {

    public boolean allowsPath(Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        return allowedPaths.stream().anyMatch(allowed -> normalized.startsWith(allowed));
    }

    /** A project has exactly one explicit GitHub delivery target; it is never inferred from a PR URL. */
    public record GitHubDelivery(String repository, String baseBranch, Set<String> allowedMergeLogins,
            String requiredCheckName, String testEnvironment) {
    }

    /** ADR-0006 gate policy. Projects may only tighten the global defaults: add categories, shorten retention. */
    public record QualityPolicy(List<String> requiredCategories, Map<String, String> categoryMappings, boolean autoMerge,
            int evidenceRetentionDays, boolean subtractiveSelectionRequested, int recallGuardRuns, int graphMaxAgeDays,
            int graphMaxMergeLag) {

        /** Category derivation is mapping-driven; an event's self-declared category is never trusted. */
        public String categoryForCheckName(String checkName) {
            return categoryMappings.get(checkName);
        }
    }

    /** Test and bootstrap default: BUILD required, one mapped check, conservative limits. */
    public static QualityPolicy defaultPolicy() {
        return new QualityPolicy(List.of("BUILD"), Map.of("forgeops-test", "BUILD"), false, 30, false, 20, 7, 50);
    }
}
