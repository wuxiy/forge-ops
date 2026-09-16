package com.company.forgeops.v2.verification;

import com.company.forgeops.v2.agent.domain.AgentRole;
import com.company.forgeops.v2.agent.domain.AgentRun;
import com.company.forgeops.v2.agent.domain.AgentRunRepository;
import com.company.forgeops.v2.agent.domain.AgentRunState;
import com.company.forgeops.v2.agent.execution.AgentContracts;
import com.company.forgeops.v2.audit.AuditTrail;
import com.company.forgeops.v2.feedback.domain.Feedback;
import com.company.forgeops.v2.feedback.domain.FeedbackRepository;
import com.company.forgeops.v2.integration.domain.OutboxEvent;
import com.company.forgeops.v2.integration.domain.OutboxEventRepository;
import com.company.forgeops.v2.integration.github.GitHubMergeClient;
import com.company.forgeops.v2.observability.ForgeOpsMetrics;
import com.company.forgeops.v2.integration.github.GitHubProviderUnavailableException;
import com.company.forgeops.v2.integration.github.GitHubWebhookProperties;
import com.company.forgeops.v2.registry.ProjectCatalog;
import com.company.forgeops.v2.registry.ResolvedProject;
import com.company.forgeops.v2.verification.domain.DeliveryEvidence;
import com.company.forgeops.v2.verification.domain.VerificationEvidence;
import com.company.forgeops.v2.verification.domain.VerificationEvidenceRepository;
import com.company.forgeops.v2.verification.domain.VerificationPlan;
import com.company.forgeops.v2.verification.domain.VerificationPlanRepository;
import com.company.forgeops.v2.verification.domain.VerificationPlanStatus;
import com.company.forgeops.v2.verification.domain.VerificationRun;
import com.company.forgeops.v2.verification.domain.VerificationRunRepository;
import com.company.forgeops.v2.verification.domain.VerificationSelectionState;
import com.company.forgeops.v2.verification.domain.VerificationSelectionStateRepository;
import com.company.forgeops.v2.verification.gate.VerificationGate;
import com.company.forgeops.v2.verification.graph.VerificationGraphService;
import com.company.forgeops.v2.workflow.FeedbackWorkflow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Verification layer orchestration (ADR-0002/0005/0007/0009/0011). The plan owns planner dispatch and
 * deterministic fallback; the gate owns the only path to GATE_PASS and to a machine-identity merge.
 */
@Service
public class VerificationService {

    private final VerificationPlanRepository plans;
    private final VerificationRunRepository verificationRuns;
    private final VerificationEvidenceRepository evidenceRepo;
    private final VerificationSelectionStateRepository selectionStates;
    private final AgentRunRepository agentRuns;
    private final DeliveryEvidenceGateway deliveryEvidenceGateway;
    private final OutboxEventRepository outboxEvents;
    private final FeedbackRepository feedbacks;
    private final FeedbackWorkflow workflow;
    private final ProjectCatalog catalog;
    private final VerificationGraphService graph;
    private final VerificationProperties properties;
    private final GitHubWebhookProperties github;
    private final GitHubMergeClient mergeClient;
    private final AuditTrail auditTrail;
    private final ObjectMapper json;
    private final ForgeOpsMetrics metrics;

    public VerificationService(VerificationPlanRepository plans, VerificationRunRepository verificationRuns,
            VerificationEvidenceRepository evidenceRepo, VerificationSelectionStateRepository selectionStates,
            AgentRunRepository agentRuns, @org.springframework.context.annotation.Lazy DeliveryEvidenceGateway deliveryEvidenceGateway,
            OutboxEventRepository outboxEvents, FeedbackRepository feedbacks, FeedbackWorkflow workflow,
            ProjectCatalog catalog, VerificationGraphService graph, VerificationProperties properties,
            GitHubWebhookProperties github, GitHubMergeClient mergeClient, AuditTrail auditTrail, ObjectMapper json,
            ForgeOpsMetrics metrics) {
        this.plans = plans;
        this.verificationRuns = verificationRuns;
        this.evidenceRepo = evidenceRepo;
        this.selectionStates = selectionStates;
        this.agentRuns = agentRuns;
        this.deliveryEvidenceGateway = deliveryEvidenceGateway;
        this.outboxEvents = outboxEvents;
        this.feedbacks = feedbacks;
        this.workflow = workflow;
        this.catalog = catalog;
        this.graph = graph;
        this.properties = properties;
        this.github = github;
        this.mergeClient = mergeClient;
        this.auditTrail = auditTrail;
        this.json = json;
        this.metrics = metrics;
    }

    /** Read-only gateway to delivery evidence so this service never circularly depends on the verifier. */
    public interface DeliveryEvidenceGateway {
        DeliveryEvidence findById(UUID id);
    }

    /** Entry point after a provider-verified PR fact moved the Feedback to PR_READY. */
    @Transactional
    public void onDeliveryEvidenceVerified(UUID deliveryEvidenceId, String traceId) {
        DeliveryEvidence delivery = deliveryEvidenceGateway.findById(deliveryEvidenceId);
        if (delivery == null) {
            throw new IllegalArgumentException("Delivery evidence does not exist: " + deliveryEvidenceId);
        }
        Feedback feedback = feedbacks.findById(delivery.getFeedbackId()).orElseThrow();
        ResolvedProject project = catalog.require(feedback.getProjectId());
        if (plans.findByDeliveryEvidenceIdAndStatusIn(delivery.getId(),
                List.of(VerificationPlanStatus.PLANNED, VerificationPlanStatus.RUNNING)).isPresent()) {
            return;
        }
        // ADR-0011: an unchanged tree re-plans on the existing open plan; nothing is billed twice.
        if (plans.findFirstByCycleIdAndPrHeadShaAndStatusIn(delivery.getCycleId(), delivery.getExpectedHeadSha(),
                List.of(VerificationPlanStatus.PLANNED, VerificationPlanStatus.RUNNING)).isPresent()) {
            return;
        }
        List<String> diffFiles = codingChangedFiles(delivery.getAgentRunId());
        var impact = graph.impactSet(feedback.getProjectId(), diffFiles, project.qualityPolicy().graphMaxAgeDays(),
                null, project.qualityPolicy().graphMaxMergeLag());
        if (impact.fallbackReason() != null) {
            auditTrail.record(feedback.getId(), delivery.getCycleId(), null, "verification", "GRAPH_FALLBACK", "SUCCESS",
                    traceId, "{\"reason\":\"" + impact.fallbackReason() + "\"}");
        }
        String requiredJson = categoriesJson(project.qualityPolicy().requiredCategories());
        String mode = effectiveSelectionMode(feedback.getProjectId(), project) ? "SUBTRACTIVE" : "ADDITIVE";
        int plannerAttemptsUsed = agentRuns.findByFeedbackId(feedback.getId()).stream()
                .filter(run -> run.getRole() == AgentRole.VERIFICATION && run.getCycleId().equals(delivery.getCycleId()))
                .toList().size();
        boolean budgetAvailable = plannerAttemptsUsed < properties.getPlannerBudgetPerCycle();
        VerificationPlan plan = plans.save(VerificationPlan.create(feedback.getId(), delivery.getCycleId(),
                delivery.getId(), delivery.getExpectedHeadSha(), delivery.getExpectedPrUrl(), "PLANNER", null, mode,
                requiredJson, requiredJson, filesJson(diffFiles), impact.baselineCommit()));
        workflow.beginVerification(feedback.getId(), delivery.getCycleId(), plan.getId(), traceId);
        if (properties.isPlannerEnabled() && budgetAvailable) {
            int attempt = plannerAttemptsUsed + 1;
            enqueuePlanner(plan, feedback, attempt, traceId);
            return;
        }
        activateFallback(plan, feedback, requiredJson, filesJson(diffFiles), impact.baselineCommit(),
                budgetAvailable ? "PLANNER_DISABLED" : "PLANNER_BUDGET_EXCEEDED", traceId);
    }

    /** VER-04: only a complete, schema-valid planner output activates a plan; everything else is retryable. */
    @Transactional
    public void onPlannerResult(UUID agentRunId, AgentContracts.VerificationPlanResult result, String resultJson,
            String traceId) {
        VerificationPlan plan = plans.findByAgentRunId(agentRunId).orElse(null);
        if (plan == null || plan.getStatus() != VerificationPlanStatus.PLANNED) {
            return;
        }
        Feedback feedback = feedbacks.findById(plan.getFeedbackId()).orElseThrow();
        ResolvedProject project = catalog.require(feedback.getProjectId());
        Set<String> required = new LinkedHashSet<>(project.qualityPolicy().requiredCategories());
        // Monotonic tightening (ADR-0007/VER-24): planner selections may only add; skips are recorded, never applied
        // while selection is additive, and the deterministic risk may only be raised.
        Set<String> selected = new LinkedHashSet<>(required);
        result.selectedCategories().forEach(selection -> selected.add(selection.category()));
        if (!result.skippedCategories().isEmpty()) {
            auditTrail.record(feedback.getId(), plan.getCycleId(), agentRunId, "verification", "LLM_SHRINK_IGNORED",
                    "SUCCESS", traceId, "{\"skipped\":" + result.skippedCategories().size() + "}");
        }
        String effectiveRisk = raiseRisk(ruleRisk(selected.size()), result.riskLevel());
        plan.activate(categoriesJson(List.copyOf(required)), categoriesJson(List.copyOf(selected)), plan.getImpactFiles(),
                effectiveRisk, plan.getGraphBaselineCommit());
        auditTrail.record(feedback.getId(), plan.getCycleId(), agentRunId, "verification", "PLAN_ACTIVATED", "SUCCESS",
                traceId, "{\"origin\":\"PLANNER\",\"risk\":\"" + effectiveRisk + "\"}");
        // Evidence may already have arrived while the planner was running.
        maybeEvaluate(plans.findById(plan.getId()).orElseThrow(), traceId);
    }

    /** VER-28: unavailable, timed-out, exhausted or over-budget planner paths fall back deterministically. */
    @Transactional
    public void onPlannerFailure(UUID agentRunId, AgentRunState state, String category, String traceId) {
        VerificationPlan plan = plans.findByAgentRunId(agentRunId).orElse(null);
        if (plan == null || plan.getStatus() != VerificationPlanStatus.PLANNED) {
            return;
        }
        // One active run per (cycle, role): terminate the failed attempt before another is queued.
        workflow.recordRuntimeFailure(agentRunId, state, category == null ? "PLANNER_FAILED" : category, traceId);
        Feedback feedback = feedbacks.findById(plan.getFeedbackId()).orElseThrow();
        int attemptsUsed = agentRuns.findByFeedbackId(feedback.getId()).stream()
                .filter(run -> run.getRole() == AgentRole.VERIFICATION && run.getCycleId().equals(plan.getCycleId()))
                .toList().size();
        if (state == AgentRunState.TIMED_OUT) {
            activateFallback(plan, feedback, plan.getRequiredCategories(), plan.getImpactFiles(),
                    plan.getGraphBaselineCommit(), "PLANNER_TIMEOUT", traceId);
            return;
        }
        if (attemptsUsed < properties.getPlannerMaxAttempts()
                && attemptsUsed < properties.getPlannerBudgetPerCycle()) {
            enqueuePlanner(plan, feedback, attemptsUsed + 1, traceId);
            return;
        }
        activateFallback(plan, feedback, plan.getRequiredCategories(), plan.getImpactFiles(),
                plan.getGraphBaselineCommit(), "PLANNER_RETRIES_EXHAUSTED", traceId);
    }

    /** VER-02: any fact for another head SHA is rejected with an audit record; the state never regresses. */
    @Transactional
    public boolean onCheckRunEvidence(UUID feedbackId, UUID cycleId, String checkRunName, long checkRunId,
            String headSha, String conclusion, String payloadJson, String traceId) {
        Feedback feedback = feedbacks.findById(feedbackId).orElse(null);
        if (feedback == null || !feedback.getCurrentCycleId().equals(cycleId)) {
            return false;
        }
        VerificationPlan plan = plans.findFirstByCycleIdAndPrHeadShaAndStatusIn(cycleId, headSha,
                List.of(VerificationPlanStatus.PLANNED, VerificationPlanStatus.RUNNING)).orElse(null);
        if (plan == null) {
            auditTrail.record(feedbackId, cycleId, null, "github", "EVIDENCE_REJECTED", "REJECTED", traceId,
                    "{\"reason\":\"NO_OPEN_PLAN_FOR_HEAD\"}");
            metrics.evidenceRejected("NO_OPEN_PLAN_FOR_HEAD");
            return false;
        }
        ResolvedProject project = catalog.require(feedback.getProjectId());
        String category = project.qualityPolicy().categoryForCheckName(checkRunName);
        if (category == null) {
            // VER-09: a check that is not registry-mapped can never move the gate, whatever it claims.
            auditTrail.record(feedbackId, cycleId, null, "github", "EVIDENCE_REJECTED", "REJECTED", traceId,
                    "{\"reason\":\"UNMAPPED_CHECK\",\"check\":\"" + checkRunName + "\"}");
            metrics.evidenceRejected("UNMAPPED_CHECK");
            return false;
        }
        if (!headSha.equals(plan.getPrHeadSha())) {
            auditTrail.record(feedbackId, cycleId, null, "github", "EVIDENCE_REJECTED", "REJECTED", traceId,
                    "{\"reason\":\"STALE_HEAD_SHA\"}");
            metrics.evidenceRejected("STALE_HEAD_SHA");
            return false;
        }
        String digest = sha256(payloadJson);
        if (evidenceRepo.existsByPlanIdAndPayloadDigest(plan.getId(), digest)) {
            return true;
        }
        String normalizedConclusion = switch (conclusion == null ? "" : conclusion) {
            case "success" -> "SUCCESS";
            case "failure", "error" -> "FAILURE";
            default -> "NEUTRAL";
        };
        int retentionDays = project.qualityPolicy().evidenceRetentionDays();
        VerificationEvidence evidence = evidenceRepo.save(VerificationEvidence.record(plan.getId(), null,
                VerificationEvidence.Kind.CHECK_RUN, VerificationEvidence.Source.PR_CHECK, category, headSha,
                VerificationEvidence.Conclusion.valueOf(normalizedConclusion), digest,
                "{\"checkRunName\":\"" + AgentContracts.redactText(checkRunName) + "\",\"conclusion\":\"" + normalizedConclusion + "\"}",
                OffsetDateTime.now().plus(Duration.ofDays(retentionDays))));
        VerificationRun run = verificationRuns.save(VerificationRun.pending(plan.getId(), category,
                VerificationRun.Source.PR_CHECK, headSha, 1, null));
        run.markRunning(String.valueOf(checkRunId));
        if ("SUCCESS".equals(normalizedConclusion)) {
            run.markPassed();
        } else if ("FAILURE".equals(normalizedConclusion)) {
            run.markFailed("CHECK_FAILED", "GitHub check run concluded " + conclusion);
        }
        auditTrail.record(feedbackId, cycleId, null, "github", "VERIFICATION_EVIDENCE_RECORDED", "SUCCESS", traceId,
                "{\"planId\":\"" + plan.getId() + "\",\"category\":\"" + category + "\",\"evidenceId\":\""
                        + evidence.getId() + "\"}");
        maybeEvaluate(plan, traceId);
        return true;
    }

    /**
     * The gate decides only when every required category has a current gating fact, or when a failure fact is
     * definitive; partial evidence never fails the cycle prematurely (VER-01/VER-12).
     */
    private void maybeEvaluate(VerificationPlan plan, String traceId) {
        if (plan.getStatus() != VerificationPlanStatus.RUNNING) {
            return;
        }
        List<VerificationGate.EvidenceFact> facts = evidenceRepo.findByPlanIdOrderByCollectedAtAsc(plan.getId()).stream()
                .map(VerificationGate.EvidenceFact::from).toList();
        List<String> required = parseCategories(plan.getRequiredCategories());
        boolean anyFailure = facts.stream().filter(VerificationGate.EvidenceFact::gatesPr)
                .anyMatch(fact -> fact.currentFor(plan.getPrHeadSha()) && "FAILURE".equals(fact.conclusion()));
        boolean covered = required.stream().allMatch(category -> facts.stream()
                .filter(VerificationGate.EvidenceFact::gatesPr)
                .anyMatch(fact -> category.equals(fact.category()) && fact.currentFor(plan.getPrHeadSha())));
        if (anyFailure || covered) {
            evaluateGate(plan.getId(), traceId);
        }
    }

    /** Deterministic gate evaluation; the only source of GATE_PASS (ADR-0002) and of autoMerge eligibility. */
    @Transactional
    public void evaluateGate(UUID planId, String traceId) {
        VerificationPlan plan = plans.findById(planId).orElseThrow();
        if (plan.getStatus() != VerificationPlanStatus.RUNNING) {
            return;
        }
        Feedback feedback = feedbacks.findById(plan.getFeedbackId()).orElseThrow();
        ResolvedProject project = catalog.require(feedback.getProjectId());
        List<VerificationGate.EvidenceFact> facts = evidenceRepo.findByPlanIdOrderByCollectedAtAsc(plan.getId()).stream()
                .map(VerificationGate.EvidenceFact::from).toList();
        List<String> required = parseCategories(plan.getRequiredCategories());
        var evaluation = VerificationGate.evaluate(required, facts, plan.getPrHeadSha());
        metrics.gateDecision(evaluation.decision().name());
        String reasons = reasonsJson(evaluation.reasons());
        if (evaluation.decision() == VerificationGate.Decision.PASS) {
            plan.complete("PASS", reasons);
            workflow.recordGateOutcome(feedback.getId(), plan.getCycleId(), plan.getId(), "PASS", reasons, traceId);
            if (project.qualityPolicy().autoMerge()) {
                attemptMachineMerge(plan, feedback, project, traceId);
            } else {
                auditTrail.record(feedback.getId(), plan.getCycleId(), null, "gate", "AUTO_MERGE_REJECTED", "REJECTED",
                        traceId, "{\"reason\":\"POLICY_DISABLED\"}");
            }
            return;
        }
        plan.complete(evaluation.decision().name(), reasons);
        workflow.recordGateOutcome(feedback.getId(), plan.getCycleId(), plan.getId(), evaluation.decision().name(),
                reasons, traceId);
    }

    /** VER-13: WARN, missing or failed evidence, SHA mismatch and disabled policy never merge; each refusal is audited. */
    private void attemptMachineMerge(VerificationPlan plan, Feedback feedback, ResolvedProject project, String traceId) {
        List<VerificationGate.EvidenceFact> facts = evidenceRepo.findByPlanIdOrderByCollectedAtAsc(plan.getId()).stream()
                .map(VerificationGate.EvidenceFact::from).toList();
        boolean allCurrent = facts.stream().filter(VerificationGate.EvidenceFact::gatesPr)
                .allMatch(fact -> fact.currentFor(plan.getPrHeadSha()));
        if (!allCurrent) {
            auditTrail.record(feedback.getId(), plan.getCycleId(), null, "gate", "AUTO_MERGE_REJECTED", "REJECTED",
                    traceId, "{\"reason\":\"SHA_MISMATCH\"}");
            return;
        }
        DeliveryEvidence delivery = deliveryEvidenceGateway.findById(plan.getDeliveryEvidenceId());
        try {
            mergeClient.mergePullRequest(project.github().repository(), delivery.getPullRequestNo(),
                    plan.getPrHeadSha());
            workflow.recordAuthorizedMerge(feedback.getId(), plan.getCycleId(), delivery.getAgentRunId(),
                    github.getMachineMergeLogin(), traceId);
            auditTrail.record(feedback.getId(), plan.getCycleId(), null, "gate:" + github.getMachineMergeLogin(),
                    "MACHINE_MERGE_EXECUTED", "SUCCESS", traceId,
                    "{\"planId\":\"" + plan.getId() + "\",\"actorType\":\"MACHINE\"}");
        } catch (GitHubMergeClient.MergeRejected rejected) {
            auditTrail.record(feedback.getId(), plan.getCycleId(), null, "gate", "AUTO_MERGE_REJECTED", "REJECTED",
                    traceId, "{\"reason\":\"" + rejected.getMessage() + "\"}");
        } catch (GitHubProviderUnavailableException unavailable) {
            auditTrail.record(feedback.getId(), plan.getCycleId(), null, "gate", "AUTO_MERGE_DEFERRED", "FAILED",
                    traceId, "{\"reason\":\"GITHUB_UNAVAILABLE\"}");
        }
    }

    /** VER-02: a new push expires every open plan and its evidence for that PR; the state machine never regresses. */
    @Transactional
    public int onPullRequestSynchronized(String repository, long pullRequestNo, String newHeadSha, String traceId) {
        int expired = 0;
        for (VerificationPlan plan : plans.findAll()) {
            if (plan.getStatus() != VerificationPlanStatus.PLANNED && plan.getStatus() != VerificationPlanStatus.RUNNING) {
                continue;
            }
            DeliveryEvidence delivery = deliveryEvidenceGateway.findById(plan.getDeliveryEvidenceId());
            if (delivery == null || !delivery.getRepository().equals(repository)
                    || delivery.getPullRequestNo() != pullRequestNo || delivery.getExpectedHeadSha().equals(newHeadSha)) {
                continue;
            }
            plan.expire();
            expired++;
            auditTrail.record(plan.getFeedbackId(), plan.getCycleId(), null, "github", "PLAN_EXPIRED_NEW_PUSH",
                    "SUCCESS", traceId, "{\"planId\":\"" + plan.getId() + "\",\"newHead\":\"" + newHeadSha + "\"}");
        }
        return expired;
    }

    /** VER-25/26: scheduled full-suite facts bind repo+branch+commit+workflow and feed the recall circuit breaker. */
    @Transactional
    public boolean onScheduledWorkflowRun(ResolvedProject project, String workflowName, String branch, String headSha,
            String conclusion, String payloadJson, String traceId) {
        String category = project.qualityPolicy().categoryForCheckName(workflowName);
        List<VerificationPlan> subtractivePass = plans.findAll().stream()
                .filter(plan -> "SUBTRACTIVE".equals(plan.getSelectionMode()))
                .filter(plan -> "PASS".equals(plan.getGateDecision()))
                .toList();
        boolean missedRegression = "failure".equals(conclusion) && !subtractivePass.isEmpty();
        if (missedRegression) {
            for (VerificationPlan plan : subtractivePass) {
                VerificationSelectionState state = selectionStates.findById(project.id())
                        .orElseGet(() -> selectionStates.save(VerificationSelectionState.fresh(project.id())));
                state.openBreaker("NIGHTLY_MISS_AFTER_SUBTRACTIVE_PASS:" + plan.getId());
                metrics.selectionBreakerOpened();
                auditTrail.record(plan.getFeedbackId(), plan.getCycleId(), null, "verification",
                        "SELECTION_BREAKER_OPENED", "FAILED", traceId,
                        "{\"workflow\":\"" + workflowName + "\",\"head\":\"" + headSha + "\"}");
            }
        }
        auditTrail.record(null, null, null, "github:scheduled", "SCHEDULED_RUN_RECORDED", "SUCCESS", traceId,
                "{\"repository\":\"" + project.github().repository() + "\",\"workflow\":\"" + workflowName
                        + "\",\"branch\":\"" + branch + "\",\"head\":\"" + headSha + "\",\"conclusion\":\"" + conclusion
                        + "\",\"digest\":\"" + sha256(payloadJson) + "\"}");
        return !missedRegression;
    }

    /** Re-enabling subtractive selection requires the recorded Owner review and the recall guard (VER-25). */
    @Transactional
    public boolean enableSubtractiveAfterOwnerReview(String projectId, String reviewer, String traceId) {
        ResolvedProject project = catalog.require(projectId);
        VerificationSelectionState state = selectionStates.findById(projectId)
                .orElseGet(() -> selectionStates.save(VerificationSelectionState.fresh(projectId)));
        auditTrail.record(null, null, null, reviewer, "OWNER_REVIEW_RECORDED", "SUCCESS", traceId,
                "{\"projectId\":\"" + projectId + "\"}");
        if (state.isBreakerOpen()) {
            state.clearBreakerAfterOwnerReview();
            auditTrail.record(null, null, null, reviewer, "BREAKER_CLEARED_BY_OWNER_REVIEW", "SUCCESS", traceId,
                    "{\"projectId\":\"" + projectId + "\"}");
        }
        boolean enabled = state.tryEnableSubtractive(project.qualityPolicy().recallGuardRuns());
        auditTrail.record(null, null, null, reviewer, enabled ? "SUBTRACTIVE_ENABLED" : "SUBTRACTIVE_ENABLE_REJECTED",
                enabled ? "SUCCESS" : "REJECTED", traceId,
                "{\"projectId\":\"" + projectId + "\",\"guard\":" + state.getConsecutiveFullRecallRuns() + "}");
        return enabled;
    }

    /** VER-17: expired evidence keeps its digest row, purges payload and artifacts, and the purge is audited. */
    @Transactional
    public int purgeExpiredEvidence() {
        List<VerificationEvidence> expired = evidenceRepo.findByExpiresAtBeforeAndPurgedAtIsNull(OffsetDateTime.now());
        for (VerificationEvidence evidence : expired) {
            evidence.purge();
            auditTrail.record(null, null, null, "retention", "EVIDENCE_PURGED", "SUCCESS", null,
                    "{\"evidenceId\":\"" + evidence.getId() + "\",\"digest\":\"" + evidence.getPayloadDigest() + "\"}");
        }
        return expired.size();
    }

    /** VER-28: planner runs stuck past the timeout fall back deterministically instead of blocking the chain. */
    @Transactional
    public int reconcileStuckPlans() {
        int recovered = 0;
        OffsetDateTime deadline = OffsetDateTime.now().minus(properties.getPlannerTimeout());
        for (VerificationPlan plan : plans.findAll()) {
            if (plan.getStatus() != VerificationPlanStatus.PLANNED || plan.getAgentRunId() == null) {
                continue;
            }
            AgentRun run = agentRuns.findById(plan.getAgentRunId()).orElse(null);
            if (run == null || run.getCreatedAt().isAfter(deadline)) {
                continue;
            }
            if (run.getState() == AgentRunState.QUEUED) {
                onPlannerFailure(run.getId(), AgentRunState.FAILED, "PLANNER_UNAVAILABLE", "reconciler:" + plan.getId());
                recovered++;
                metrics.stuckPlanRecovered();
            } else if (run.getState() == AgentRunState.RUNNING) {
                onPlannerFailure(run.getId(), AgentRunState.TIMED_OUT, "PLANNER_TIMEOUT", "reconciler:" + plan.getId());
                recovered++;
                metrics.stuckPlanRecovered();
            }
        }
        return recovered;
    }

    private void enqueuePlanner(VerificationPlan plan, Feedback feedback, int attempt, String traceId) {
        // The previous attempt is terminal by contract; the unique active-run index enforces it at the database.
        AgentRun previous = plan.getAgentRunId() == null ? null : agentRuns.findById(plan.getAgentRunId()).orElse(null);
        if (previous != null && (previous.getState() == AgentRunState.QUEUED
                || previous.getState() == AgentRunState.RUNNING)) {
            throw new IllegalStateException("Previous planner attempt is still active: " + previous.getId());
        }
        AgentRun run = agentRuns.save(AgentRun.queue(feedback.getId(), plan.getCycleId(), AgentRole.VERIFICATION,
                attempt, feedback.getId() + "/" + plan.getCycleId() + "/VERIFICATION/" + attempt));
        plan.attachPlannerRun(run.getId());
        outbox(run);
        auditTrail.record(feedback.getId(), plan.getCycleId(), run.getId(), "system", "PLANNER_QUEUED", "SUCCESS",
                traceId, "{\"planId\":\"" + plan.getId() + "\",\"attempt\":" + attempt + "}");
    }

    private void outbox(AgentRun run) {
        String payload = "{\"agentRunId\":\"" + run.getId() + "\"}";
        outboxEvents.save(OutboxEvent.pending("AGENT_RUN", run.getId(), "AGENT_RUN_REQUESTED", run.getIdempotencyKey(), payload));
    }

    private void activateFallback(VerificationPlan plan, Feedback feedback, String requiredJson, String impactJson,
            String baselineCommit, String reason, String traceId) {
        plan.markFallback(reason);
        plan.activate(requiredJson, requiredJson, impactJson, ruleRisk(parseCategories(requiredJson).size()),
                baselineCommit);
        auditTrail.record(feedback.getId(), plan.getCycleId(), null, "verification", "FALLBACK_PLAN_ACTIVATED", "SUCCESS",
                traceId, "{\"planId\":\"" + plan.getId() + "\",\"reason\":\"" + reason + "\"}");
        metrics.plannerFallback(reason);
        maybeEvaluate(plans.findById(plan.getId()).orElseThrow(), traceId);
    }

    private boolean effectiveSelectionMode(String projectId, ResolvedProject project) {
        if (!project.qualityPolicy().subtractiveSelectionRequested()) {
            return false;
        }
        VerificationSelectionState state = selectionStates.findById(projectId).orElse(null);
        return state != null && state.isSubtractiveEnabled() && !state.isBreakerOpen();
    }

    private List<String> codingChangedFiles(UUID codingRunId) {
        return agentRuns.findById(codingRunId)
                .map(AgentRun::getResultJson)
                .map(resultJson -> {
                    try {
                        JsonNode root = json.readTree(resultJson);
                        List<String> files = new ArrayList<>();
                        for (JsonNode file : root.get("changedFiles")) {
                            files.add(file.asString());
                        }
                        return files;
                    } catch (Exception invalid) {
                        return List.<String>of();
                    }
                })
                .orElse(List.of());
    }

    private static String ruleRisk(int impactSize) {
        if (impactSize >= 6) return "CRITICAL";
        if (impactSize >= 3) return "HIGH";
        if (impactSize >= 1) return "MEDIUM";
        return "LOW";
    }

    private static String raiseRisk(String base, String plannerRisk) {
        List<String> order = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
        int baseIndex = order.indexOf(base);
        int plannerIndex = plannerRisk == null ? -1 : order.indexOf(plannerRisk);
        return plannerIndex > baseIndex ? plannerRisk : base;
    }

    private List<String> parseCategories(String categoriesJson) {
        try {
            List<String> result = new ArrayList<>();
            for (JsonNode node : json.readTree(categoriesJson)) {
                result.add(node.asString());
            }
            return result;
        } catch (Exception invalid) {
            throw new IllegalStateException("stored categories are invalid", invalid);
        }
    }

    private String categoriesJson(List<String> categories) {
        StringBuilder builder = new StringBuilder("[");
        for (String category : new LinkedHashSet<>(categories)) {
            if (builder.length() > 1) {
                builder.append(',');
            }
            builder.append('"').append(category).append('"');
        }
        return builder.append(']').toString();
    }

    private String filesJson(List<String> files) {
        return categoriesJson(files);
    }

    private String reasonsJson(List<String> reasons) {
        return categoriesJson(reasons);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }
}
