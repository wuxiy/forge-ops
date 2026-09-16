package com.company.forgeops.v2.registry;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Immutable, validated project policy used by all v2 callers. */
public record ResolvedProject(String id, Path repositoryRoot, List<Path> allowedPaths, Set<String> browserOrigins,
        GitHubDelivery github) {

    public boolean allowsPath(Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        return allowedPaths.stream().anyMatch(allowed -> normalized.startsWith(allowed));
    }

    /** A project has exactly one explicit GitHub delivery target; it is never inferred from a PR URL. */
    public record GitHubDelivery(String repository, String baseBranch, Set<String> allowedMergeLogins,
            String requiredCheckName, String testEnvironment) {
    }
}
