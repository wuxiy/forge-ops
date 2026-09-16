package com.company.forgeops.v2.verification;

import com.company.forgeops.v2.registry.GlobalQualityPolicy;
import jakarta.validation.constraints.Min;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Verification-layer deployment settings. The executor stays disabled until its host is deliberately provisioned. */
@Validated
@ConfigurationProperties("forgeops.v2.verification")
public class VerificationProperties {

    private boolean plannerEnabled = true;

    @Min(1)
    private int plannerMaxAttempts = 2;

    @Min(1)
    private int plannerBudgetPerCycle = 2;

    private Duration plannerTimeout = Duration.ofMinutes(10);

    private boolean executorEnabled = false;

    private Path executorTaskRoot = Path.of("/var/lib/forgeops/executor");

    private List<String> executorAllowedImages = new ArrayList<>();

    @Min(1)
    private int executorGlobalConcurrency = 1;

    @Min(1)
    private int executorRunTimeoutSeconds = 900;

    private final Policy policy = new Policy();

    /** Global gate-policy defaults resolved by ProjectCatalog; projects may only tighten them (ADR-0006). */
    public static class Policy {

        private List<String> requiredCategories = List.of("BUILD");

        private int defaultEvidenceRetentionDays = 30;

        private int maxEvidenceRetentionDays = 90;

        private int graphMaxAgeDays = 7;

        private int graphMaxMergeLag = 50;

        private int recallGuardRuns = 20;

        public List<String> getRequiredCategories() { return requiredCategories; }
        public void setRequiredCategories(List<String> requiredCategories) { this.requiredCategories = requiredCategories; }
        public int getDefaultEvidenceRetentionDays() { return defaultEvidenceRetentionDays; }
        public void setDefaultEvidenceRetentionDays(int days) { this.defaultEvidenceRetentionDays = days; }
        public int getMaxEvidenceRetentionDays() { return maxEvidenceRetentionDays; }
        public void setMaxEvidenceRetentionDays(int days) { this.maxEvidenceRetentionDays = days; }
        public int getGraphMaxAgeDays() { return graphMaxAgeDays; }
        public void setGraphMaxAgeDays(int days) { this.graphMaxAgeDays = days; }
        public int getGraphMaxMergeLag() { return graphMaxMergeLag; }
        public void setGraphMaxMergeLag(int lag) { this.graphMaxMergeLag = lag; }
        public int getRecallGuardRuns() { return recallGuardRuns; }
        public void setRecallGuardRuns(int runs) { this.recallGuardRuns = runs; }
    }

    /** Resolves the global defaults consumed by ProjectCatalog; projects may only tighten them (ADR-0006). */
    public GlobalQualityPolicy toGlobalQualityPolicy() {
        return new GlobalQualityPolicy(policy.getRequiredCategories(), policy.getDefaultEvidenceRetentionDays(),
                policy.getMaxEvidenceRetentionDays(), policy.getGraphMaxAgeDays(), policy.getGraphMaxMergeLag(),
                policy.getRecallGuardRuns());
    }

    public boolean isPlannerEnabled() { return plannerEnabled; }
    public void setPlannerEnabled(boolean plannerEnabled) { this.plannerEnabled = plannerEnabled; }
    public int getPlannerMaxAttempts() { return plannerMaxAttempts; }
    public void setPlannerMaxAttempts(int plannerMaxAttempts) { this.plannerMaxAttempts = plannerMaxAttempts; }
    public int getPlannerBudgetPerCycle() { return plannerBudgetPerCycle; }
    public void setPlannerBudgetPerCycle(int plannerBudgetPerCycle) { this.plannerBudgetPerCycle = plannerBudgetPerCycle; }
    public Duration getPlannerTimeout() { return plannerTimeout; }
    public void setPlannerTimeout(Duration plannerTimeout) { this.plannerTimeout = plannerTimeout; }
    public boolean isExecutorEnabled() { return executorEnabled; }
    public void setExecutorEnabled(boolean executorEnabled) { this.executorEnabled = executorEnabled; }
    public Path getExecutorTaskRoot() { return executorTaskRoot; }
    public void setExecutorTaskRoot(Path executorTaskRoot) { this.executorTaskRoot = executorTaskRoot; }
    public List<String> getExecutorAllowedImages() { return executorAllowedImages; }
    public void setExecutorAllowedImages(List<String> executorAllowedImages) { this.executorAllowedImages = executorAllowedImages; }
    public int getExecutorGlobalConcurrency() { return executorGlobalConcurrency; }
    public void setExecutorGlobalConcurrency(int executorGlobalConcurrency) { this.executorGlobalConcurrency = executorGlobalConcurrency; }
    public int getExecutorRunTimeoutSeconds() { return executorRunTimeoutSeconds; }
    public void setExecutorRunTimeoutSeconds(int executorRunTimeoutSeconds) { this.executorRunTimeoutSeconds = executorRunTimeoutSeconds; }
}
