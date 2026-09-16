package com.company.forgeops.v2.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.forgeops.GatewayApplication;
import com.company.forgeops.v2.agent.domain.AgentRole;
import com.company.forgeops.v2.agent.domain.AgentRun;
import com.company.forgeops.v2.agent.domain.AgentRunRepository;
import com.company.forgeops.v2.agent.execution.AgentContracts;
import com.company.forgeops.v2.agent.execution.AgentContracts.CodingOutcome;
import com.company.forgeops.v2.agent.execution.AgentContracts.CodingResult;
import com.company.forgeops.v2.agent.execution.AgentContracts.TriageDecision;
import com.company.forgeops.v2.agent.execution.AgentContracts.TriageResult;
import com.company.forgeops.v2.audit.AuditLogRepository;
import com.company.forgeops.v2.feedback.domain.Feedback;
import com.company.forgeops.v2.feedback.domain.FeedbackRepository;
import com.company.forgeops.v2.integration.domain.IntegrationEvent;
import com.company.forgeops.v2.integration.domain.IntegrationEventState;
import com.company.forgeops.v2.integration.github.GitHubPullRequestClient;
import com.company.forgeops.v2.integration.github.GitHubPullRequestClient.ObservedPullRequest;
import com.company.forgeops.v2.integration.inbox.InboundEvent;
import com.company.forgeops.v2.integration.inbox.InboxReceipt;
import com.company.forgeops.v2.integration.inbox.IntegrationInbox;
import com.company.forgeops.v2.verification.domain.DeliveryEvidence;
import com.company.forgeops.v2.verification.domain.DeliveryEvidenceRepository;
import com.company.forgeops.v2.verification.domain.DeliveryEvidenceState;
import com.company.forgeops.v2.verification.domain.VerificationEvidenceRepository;
import com.company.forgeops.v2.verification.domain.VerificationPlanRepository;
import com.company.forgeops.v2.verification.domain.VerificationPlanStatus;
import com.company.forgeops.v2.verification.domain.VerificationSelectionState;
import com.company.forgeops.v2.verification.domain.VerificationSelectionStateRepository;
import com.company.forgeops.v2.workflow.FeedbackState;
import com.company.forgeops.v2.workflow.FeedbackWorkflow;
import com.company.forgeops.v2.workflow.SubmitFeedbackCommand;
import com.company.forgeops.v2.workflow.WorkflowConflictException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * VER-01/02/16/17/25/28 plus WF-08/09/10 and REL-06/07 on a real isolated PostgreSQL database. Events are
 * schema-strict Inbox facts exactly like the signed webhook boundary produces after normalization.
 */
@SpringBootTest(classes = {GatewayApplication.class, VerificationLayerPostgresIT.FakeGitHub.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"forgeops.v2.security.token-secret=integration-test-security-secret",
                "forgeops.v2.registry.path=src/test/resources/v2-registry", "forgeops.v2.registry.workspace-root=.",
                "forgeops.v2.runtime.base-url=http://127.0.0.1:17678", "forgeops.v2.runtime.service-token=integration-runtime-token",
                "forgeops.v2.github.enabled=true", "forgeops.v2.github.webhook-secret=integration-webhook-secret",
                "forgeops.v2.github.api-token=integration-read-only-token",
                "forgeops.v2.verification.planner-enabled=false",
                "forgeops.v2.verification.executor-task-root=target/executor-tasks"})
class VerificationLayerPostgresIT {

    @Autowired
    private FeedbackWorkflow workflow;

    @Autowired
    private AgentRunRepository agentRuns;

    @Autowired
    private DeliveryEvidenceService deliveryEvidence;

    @Autowired
    private DeliveryEvidenceVerifier verifier;

    @Autowired
    private DeliveryEvidenceRepository deliveryEvidences;

    @Autowired
    private IntegrationInbox inbox;

    @Autowired
    private FeedbackRepository feedbacks;

    @Autowired
    private VerificationService verification;

    @Autowired
    private VerificationPlanRepository plans;

    @Autowired
    private VerificationEvidenceRepository evidenceRepo;

    @Autowired
    private VerificationSelectionStateRepository selectionStates;

    @Autowired
    private AuditLogRepository audits;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void ver01mergeBeforeGatePassIsRejectedAndOnlyTheGateReachesGatePass() {
        Prepared prepared = prepare();
        assertEquals(FeedbackState.VERIFY_RUNNING, state(prepared.feedbackId()));

        InboxReceipt prematureMerge = inbox.accept(event("merge-early-" + prepared.feedbackId(), "PULL_REQUEST",
                mergePayload(prepared.pullRequestNo(), "forgeops-test-owner")));
        // VER-01: before GATE_PASS a merge fact is refused (deferred for diagnosis) and the state cannot advance.
        assertEquals(IntegrationEventState.DEFERRED, prematureMerge.state());
        assertEquals(FeedbackState.VERIFY_RUNNING, state(prepared.feedbackId()));

        InboxReceipt gateCheck = inbox.accept(event("gate-ok-" + prepared.feedbackId(), "CHECK_RUN",
                checkRunPayload(prepared.pullRequestNo(), "abc123", "success", "a1")));
        assertEquals(IntegrationEventState.APPLIED, gateCheck.state());
        assertEquals(FeedbackState.GATE_PASS, state(prepared.feedbackId()));

        InboxReceipt merge = inbox.accept(event("merge-ok-" + prepared.feedbackId(), "PULL_REQUEST",
                mergePayload(prepared.pullRequestNo(), "forgeops-test-owner")));
        assertEquals(IntegrationEventState.APPLIED, merge.state());
        assertEquals(FeedbackState.BUILD_RUNNING, state(prepared.feedbackId()));
    }

    @Test
    void ver01verifyFailedIsSeparateFromExecutionBuildDeployFailures() {
        Prepared prepared = prepare();
        InboxReceipt failedGateCheck = inbox.accept(event("gate-bad-" + prepared.feedbackId(), "CHECK_RUN",
                checkRunPayload(prepared.pullRequestNo(), "abc123", "failure", "a2")));
        assertEquals(IntegrationEventState.APPLIED, failedGateCheck.state());
        assertEquals(FeedbackState.VERIFY_FAILED, state(prepared.feedbackId()));

        // ADR-0002: the authorized path out of VERIFY_FAILED is a new Coding attempt in the same Cycle.
        var after = workflow.retry(prepared.feedbackId(), AgentRole.CODING, "owner", "verify-retry");
        assertEquals(FeedbackState.CODE_QUEUED, after.getState());
        assertEquals(2, agentRuns.findByFeedbackId(prepared.feedbackId()).stream()
                .filter(run -> run.getRole() == AgentRole.CODING).count());
    }

    @Test
    void ver02newPushExpiresOpenPlansAndOldEvidenceIsRejectedWithAudit() {
        Prepared prepared = prepare();
        int expired = verification.onPullRequestSynchronized("example/security-project-a", prepared.pullRequestNo(),
                "def456", "push-" + prepared.feedbackId());
        assertEquals(1, expired);
        var plan = plans.findByCycleIdOrderByCreatedAtDesc(prepared.cycleId()).getFirst();
        assertEquals(VerificationPlanStatus.EXPIRED, plan.getStatus());
        assertEquals(FeedbackState.VERIFY_RUNNING, state(prepared.feedbackId()));

        InboxReceipt staleCheck = inbox.accept(event("stale-" + prepared.feedbackId(), "CHECK_RUN",
                checkRunPayload(prepared.pullRequestNo(), "abc123", "success", "a3")));
        assertEquals(IntegrationEventState.REJECTED, staleCheck.state());
        assertTrue(audits.findAll().stream().anyMatch(audit -> "EVIDENCE_REJECTED".equals(audit.getAction())));
    }

    @Test
    void ver28plannerTimeoutFallsBackToADeterministicPlanWithoutBlocking() {
        Prepared prepared = preparePlannerEnabled();
        var plan = plans.findByDeliveryEvidenceIdAndStatusIn(prepared.deliveryEvidenceId(),
                List.of(VerificationPlanStatus.PLANNED)).orElseThrow();
        assertNotNull(plan.getAgentRunId());
        verification.onPlannerFailure(plan.getAgentRunId(),
                com.company.forgeops.v2.agent.domain.AgentRunState.TIMED_OUT, "PLANNER_TIMEOUT", "fallback-it");

        plan = plans.findById(plan.getId()).orElseThrow();
        assertEquals(VerificationPlanStatus.RUNNING, plan.getStatus());
        assertEquals("FALLBACK", plan.getOrigin());
        assertEquals("PLANNER_TIMEOUT", plan.getFallbackReason());
        assertNull(plan.getAgentRunId());
        assertEquals(FeedbackState.VERIFY_RUNNING, state(prepared.feedbackId()));
        assertTrue(audits.findAll().stream().anyMatch(audit -> "FALLBACK_PLAN_ACTIVATED".equals(audit.getAction())));
    }

    @Test
    void ver28retriesExhaustedFallsBackAndReplanningReusesTheSamePlan() {
        Prepared prepared = preparePlannerEnabled();
        var plan = plans.findByDeliveryEvidenceIdAndStatusIn(prepared.deliveryEvidenceId(),
                List.of(VerificationPlanStatus.PLANNED)).orElseThrow();
        verification.onPlannerFailure(plan.getAgentRunId(), com.company.forgeops.v2.agent.domain.AgentRunState.FAILED,
                "PASEO_ERROR", "fallback-retries");
        var replanned = plans.findByDeliveryEvidenceIdAndStatusIn(prepared.deliveryEvidenceId(),
                List.of(VerificationPlanStatus.PLANNED)).orElseThrow();
        verification.onPlannerFailure(replanned.getAgentRunId(),
                com.company.forgeops.v2.agent.domain.AgentRunState.FAILED, "PASEO_ERROR", "fallback-retries-2");

        plan = plans.findById(replanned.getId()).orElseThrow();
        assertEquals("FALLBACK", plan.getOrigin());
        assertEquals("PLANNER_RETRIES_EXHAUSTED", plan.getFallbackReason());

        // Same tree re-push must not bill a second planner round (ADR-0011).
        int plansForCycle = plans.findByCycleIdOrderByCreatedAtDesc(prepared.cycleId()).size();
        verification.onDeliveryEvidenceVerified(prepared.deliveryEvidenceId(), "reuse-it");
        assertEquals(plansForCycle, plans.findByCycleIdOrderByCreatedAtDesc(prepared.cycleId()).size());
    }

    @Test
    void ver16everyVerificationFactIsAnchoredToTheSameCycleAndPrHead() {
        Prepared prepared = prepare();
        inbox.accept(event("anchor-" + prepared.feedbackId(), "CHECK_RUN",
                checkRunPayload(prepared.pullRequestNo(), "abc123", "success", "a4")));
        var plan = plans.findByDeliveryEvidenceIdAndStatusIn(prepared.deliveryEvidenceId(),
                List.of(VerificationPlanStatus.COMPLETED)).orElseThrow();
        Feedback feedback = feedbacks.findById(prepared.feedbackId()).orElseThrow();

        assertEquals(feedback.getCurrentCycleId(), plan.getCycleId());
        assertEquals(prepared.cycleId(), plan.getCycleId());
        assertEquals("abc123", plan.getPrHeadSha());
        DeliveryEvidence delivery = deliveryEvidences.findById(plan.getDeliveryEvidenceId()).orElseThrow();
        assertEquals(prepared.cycleId(), delivery.getCycleId());
        assertEquals(plan.getPrHeadSha(), delivery.getExpectedHeadSha());
        var evidence = evidenceRepo.findByPlanIdOrderByCollectedAtAsc(plan.getId());
        assertEquals(1, evidence.size());
        assertEquals("abc123", evidence.getFirst().getHeadSha());
        assertEquals(plan.getId(), evidence.getFirst().getPlanId());
    }

    @Test
    void ver17retentionPurgesPayloadsButKeepsDigestRowsAndAudits() {
        Prepared prepared = prepare();
        inbox.accept(event("retain-" + prepared.feedbackId(), "CHECK_RUN",
                checkRunPayload(prepared.pullRequestNo(), "abc123", "success", "a5")));
        var plan = plans.findByDeliveryEvidenceIdAndStatusIn(prepared.deliveryEvidenceId(),
                List.of(VerificationPlanStatus.COMPLETED)).orElseThrow();
        var evidence = evidenceRepo.findByPlanIdOrderByCollectedAtAsc(plan.getId()).getFirst();
        jdbc.update("update verification_evidence set expires_at = now() - interval '1 day' where id = ?",
                evidence.getId());

        int purged = verification.purgeExpiredEvidence();
        assertEquals(1, purged);
        var reloaded = evidenceRepo.findById(evidence.getId()).orElseThrow();
        assertNotNull(reloaded.getPurgedAt());
        assertEquals("{\"purged\": true}", reloaded.getRedactedPayload());
        assertNotNull(reloaded.getPayloadDigest());
        assertTrue(audits.findAll().stream().anyMatch(audit -> "EVIDENCE_PURGED".equals(audit.getAction())));
    }

    @Test
    void ver24additiveSelectionNeverReducesRequiredCategories() throws Exception {
        Prepared prepared = prepare();
        var plan = plans.findByDeliveryEvidenceIdAndStatusIn(prepared.deliveryEvidenceId(),
                List.of(VerificationPlanStatus.RUNNING)).orElseThrow();
        AgentRun planner = agentRuns.save(AgentRun.queue(prepared.feedbackId(), prepared.cycleId(),
                AgentRole.VERIFICATION, 1, prepared.feedbackId() + "/" + prepared.cycleId() + "/VERIFICATION/24"));
        jdbc.update("update verification_plan set status = 'PLANNED', agent_run_id = ? where id = ?",
                planner.getId(), plan.getId());
        workflow.recordRuntimeSubmission(planner.getId(), "planner-24-" + UUID.randomUUID(), "ver24");
        var plannerResult = new AgentContracts.VerificationPlanResult("LOW", "shrink everything",
                List.of(new AgentContracts.CategorySelection("E2E", "extra coverage")),
                List.of(new AgentContracts.CategorySelection("BUILD", "planner says safe to skip")));
        verification.onPlannerResult(planner.getId(), plannerResult,
                "{}", "ver24");

        var activated = plans.findByCycleIdOrderByCreatedAtDesc(prepared.cycleId()).getFirst();
        assertEquals(VerificationPlanStatus.RUNNING, activated.getStatus());
        // Additive semantics: required BUILD stays; the planner may only add E2E; the skip is ignored.
        var selected = JsonMapper.shared().readTree(activated.getSelectedCategories());
        assertEquals(2, selected.size());
        assertEquals("BUILD", selected.get(0).asString());
        assertEquals("E2E", selected.get(1).asString());
        assertTrue(audits.findAll().stream().anyMatch(audit -> "LLM_SHRINK_IGNORED".equals(audit.getAction())));
        assertTrue(audits.findAll().stream().anyMatch(audit -> "PLAN_ACTIVATED".equals(audit.getAction())));
    }

    @Test
    void ver25nightlyMissOpensTheBreakerAndOwnerReviewPlusGuardReEnables() {
        selectionStates.save(VerificationSelectionState.fresh("security-project-a"));
        Prepared prepared = prepare();
        var plan = plans.findByDeliveryEvidenceIdAndStatusIn(prepared.deliveryEvidenceId(),
                List.of(VerificationPlanStatus.RUNNING)).orElseThrow();
        jdbc.update("update verification_plan set selection_mode = 'SUBTRACTIVE', status = 'COMPLETED', "
                + "gate_decision = 'PASS' where id = ?", plan.getId());

        boolean healthy = verification.onScheduledWorkflowRun(projectContext(), "nightly-full", "main",
                "nightly-sha", "success", "{}", "nightly-ok-" + prepared.feedbackId());
        assertTrue(healthy);

        InboxReceipt nightlyReceipt = inbox.accept(new InboundEvent("GITHUB",
                "nightly-miss-" + prepared.feedbackId(), "WORKFLOW_RUN", "security-project-a", null, null, null,
                workflowRunPayload("failure"), "nightly-miss-" + prepared.feedbackId()));
        assertEquals(IntegrationEventState.APPLIED, nightlyReceipt.state());
        assertTrue(selectionStates.findById("security-project-a").orElseThrow().isBreakerOpen());
        var state = selectionStates.findById("security-project-a").orElseThrow();
        assertTrue(state.isBreakerOpen());
        assertTrue(audits.findAll().stream().anyMatch(audit -> "SELECTION_BREAKER_OPENED".equals(audit.getAction())));

        assertFalse(verification.enableSubtractiveAfterOwnerReview("security-project-a", "owner", "review-early"));
        var guard = selectionStates.findById("security-project-a").orElseThrow();
        for (int i = 0; i < 20; i++) {
            guard.recordFullRecallOutcome(true);
        }
        selectionStates.save(guard);
        assertTrue(verification.enableSubtractiveAfterOwnerReview("security-project-a", "owner", "review-late"));
        assertTrue(selectionStates.findById("security-project-a").orElseThrow().isSubtractiveEnabled());
    }

    @Test
    void rel06outOfOrderDeployDefersThenReplaysAfterItsPreconditions() {
        Prepared prepared = prepare();
        inbox.accept(event("gate-rel6-" + prepared.feedbackId(), "CHECK_RUN",
                checkRunPayload(prepared.pullRequestNo(), "abc123", "success", "a6")));
        assertEquals(FeedbackState.GATE_PASS, state(prepared.feedbackId()));

        InboxReceipt earlyDeploy = inbox.accept(event("deploy-early-" + prepared.feedbackId(), "DEPLOYMENT_STATUS",
                deploymentPayload(prepared.pullRequestNo(), "success")));
        assertEquals(IntegrationEventState.DEFERRED, earlyDeploy.state());
        assertEquals(FeedbackState.GATE_PASS, state(prepared.feedbackId()));

        inbox.accept(event("merge-rel6-" + prepared.feedbackId(), "PULL_REQUEST",
                mergePayload(prepared.pullRequestNo(), "forgeops-test-owner")));
        inbox.accept(event("ci-rel6-" + prepared.feedbackId(), "CHECK_RUN",
                checkRunPayload(prepared.pullRequestNo(), "abc123", "success", "a7")));
        assertEquals(FeedbackState.DEPLOY_RUNNING, state(prepared.feedbackId()));

        // REL-07/REL-10: replaying the deferred fact is idempotent and still honours state preconditions.
        InboxReceipt replayed = inbox.apply(earlyDeploy.eventId(), true);
        assertEquals(IntegrationEventState.APPLIED, replayed.state());
        assertEquals(FeedbackState.WAITING_VERIFY, state(prepared.feedbackId()));
    }

    @Test
    void wf10terminalDoneStateIsNeverReversedByLateExternalFacts() {
        Prepared prepared = prepare();
        inbox.accept(event("gate-wf10-" + prepared.feedbackId(), "CHECK_RUN",
                checkRunPayload(prepared.pullRequestNo(), "abc123", "success", "a8")));
        inbox.accept(event("merge-wf10-" + prepared.feedbackId(), "PULL_REQUEST",
                mergePayload(prepared.pullRequestNo(), "forgeops-test-owner")));
        inbox.accept(event("ci-wf10-" + prepared.feedbackId(), "CHECK_RUN",
                checkRunPayload(prepared.pullRequestNo(), "abc123", "success", "a9")));
        inbox.accept(event("deploy-wf10-" + prepared.feedbackId(), "DEPLOYMENT_STATUS",
                deploymentPayload(prepared.pullRequestNo(), "success")));
        workflow.recordVerification(prepared.feedbackId(), "reporter",
                com.company.forgeops.v2.verification.domain.VerificationResult.PASS, "verified",
                null, null, 0, "wf10");
        assertEquals(FeedbackState.DONE, state(prepared.feedbackId()));

        InboxReceipt lateMerge = inbox.accept(event("late-merge-" + prepared.feedbackId(), "PULL_REQUEST",
                mergePayload(prepared.pullRequestNo(), "forgeops-test-owner")));
        InboxReceipt lateCi = inbox.accept(event("late-ci-" + prepared.feedbackId(), "CHECK_RUN",
                checkRunPayload(prepared.pullRequestNo(), "abc123", "failure", "aa")));
        InboxReceipt lateDeploy = inbox.accept(event("late-deploy-" + prepared.feedbackId(), "DEPLOYMENT_STATUS",
                deploymentPayload(prepared.pullRequestNo(), "failure")));
        assertEquals(IntegrationEventState.REJECTED, lateMerge.state());
        assertEquals(IntegrationEventState.REJECTED, lateCi.state());
        assertEquals(IntegrationEventState.REJECTED, lateDeploy.state());
        assertEquals(FeedbackState.DONE, state(prepared.feedbackId()));
    }

    @Test
    void wf09concurrentTransitionsAdmitExactlyOneWinner() throws Exception {
        var feedback = workflow.submit(new SubmitFeedbackCommand("security-project-a", "concurrent", "t", "d",
                "{\"safe\":true}", "f".repeat(64), 0, "wf09"));
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        workflow.transition(feedback.getId(), FeedbackState.TRIAGE_RUNNING, "racer-" + Thread.currentThread().getId(),
                                "wf09");
                        return true;
                    } catch (RuntimeException conflict) {
                        return false;
                    }
                }));
            }
            start.countDown();
            for (Future<Boolean> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) {
                    successes.incrementAndGet();
                }
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, successes.get());
        assertEquals(FeedbackState.TRIAGE_RUNNING, state(feedback.getId()));
    }

    @Test
    void illegalTransitionRaisesAnExplicitConflict() {
        var feedback = workflow.submit(new SubmitFeedbackCommand("security-project-a", "subject", "t", "d",
                "{\"safe\":true}", "9".repeat(64), 0, "illegal"));
        org.junit.jupiter.api.Assertions.assertThrows(WorkflowConflictException.class,
                () -> workflow.transition(feedback.getId(), FeedbackState.DONE, "intruder", "illegal"));
    }

    private com.company.forgeops.v2.registry.ResolvedProject projectContext() {
        return new com.company.forgeops.v2.registry.ResolvedProject("security-project-a", java.nio.file.Path.of("."),
                List.of(java.nio.file.Path.of(".")), java.util.Set.of("https://app.test"),
                new com.company.forgeops.v2.registry.ResolvedProject.GitHubDelivery("example/security-project-a",
                        "main", java.util.Set.of("forgeops-test-owner"), "forgeops-test", "test"),
                com.company.forgeops.v2.registry.ResolvedProject.defaultPolicy());
    }

    private FeedbackState state(UUID feedbackId) {
        return feedbacks.findById(feedbackId).orElseThrow().getState();
    }

    private Prepared prepare() {
        long pullRequestNo = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000L) + 1;
        var feedback = workflow.submit(new SubmitFeedbackCommand("security-project-a", "reporter", "title", "desc",
                "{\"safe\":true}", "1".repeat(64), 0, "ver-it"));
        UUID cycleId = feedback.getCurrentCycleId();
        var triage = agentRuns.findByFeedbackId(feedback.getId()).getFirst();
        workflow.recordRuntimeSubmission(triage.getId(), "triage-" + UUID.randomUUID(), "ver-it");
        var triageResult = new TriageResult(TriageDecision.PROCEED_CODING, "s", "rc", List.of(), List.of(), List.of(),
                List.of(), List.of());
        workflow.recordTriageSuccess(triage.getId(), triageResult.decision(),
                AgentContracts.canonicalTriageJson(JsonMapper.shared(), triageResult), "ver-it");
        var coding = agentRuns.findByFeedbackId(feedback.getId()).stream()
                .filter(run -> run.getRole() == AgentRole.CODING).findFirst().orElseThrow();
        workflow.recordRuntimeSubmission(coding.getId(), "coding-" + UUID.randomUUID(), "ver-it");
        var declaration = new CodingResult(CodingOutcome.PR_CREATED, "forgeops/v2-ver", "abc123",
                "https://github.com/example/security-project-a/pull/" + pullRequestNo, List.of("src/App.java"),
                List.of("mvn test"), List.of(), null, null);
        deliveryEvidence.registerCodingPrDeclaration(coding.getId(), declaration,
                AgentContracts.canonicalCodingJson(JsonMapper.shared(), declaration), "ver-it");
        verifier.reconcilePending();
        UUID deliveryEvidenceId = deliveryEvidences.findByAgentRunId(coding.getId()).orElseThrow().getId();
        return new Prepared(feedback.getId(), cycleId, deliveryEvidenceId, pullRequestNo);
    }

    private Prepared preparePlannerEnabled() {
        // planner-enabled=false for the Spring context, so planner runs are enqueued manually through the service.
        Prepared prepared = prepare();
        jdbc.update("update verification_plan set status = 'PLANNED', origin = 'PLANNER', agent_run_id = null "
                + "where delivery_evidence_id = ?", prepared.deliveryEvidenceId());
        var plan = plans.findByDeliveryEvidenceIdAndStatusIn(prepared.deliveryEvidenceId(),
                List.of(VerificationPlanStatus.PLANNED)).orElseThrow();
        Feedback feedback = feedbacks.findById(prepared.feedbackId()).orElseThrow();
        AgentRun planner = agentRuns.save(AgentRun.queue(feedback.getId(), prepared.cycleId(),
                AgentRole.VERIFICATION, 1, feedback.getId() + "/" + prepared.cycleId() + "/VERIFICATION/1"));
        jdbc.update("update verification_plan set agent_run_id = ? where id = ?", planner.getId(), plan.getId());
        return prepared;
    }

    private InboundEvent event(String externalId, String type, String payload) {
        return new InboundEvent("GITHUB", externalId, type, "security-project-a", null, null, null, payload,
                "ver-it:" + externalId);
    }

    private static String mergePayload(long pullRequestNo, String mergedBy) {
        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("payloadSha256", "2".repeat(64));
            payload.put("repository", "example/security-project-a");
            payload.put("action", "closed");
            payload.put("number", pullRequestNo);
            payload.put("baseBranch", "main");
            payload.put("headBranch", "forgeops/v2-ver");
            payload.put("headSha", "abc123");
            payload.put("draft", false);
            payload.put("merged", true);
            payload.put("mergedBy", mergedBy);
            return JsonMapper.shared().writeValueAsString(payload);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String checkRunPayload(long pullRequestNo, String headSha, String conclusion, String digestTag) {
        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("payloadSha256", "3".repeat(62) + digestTag);
            payload.put("repository", "example/security-project-a");
            payload.put("action", "completed");
            payload.put("checkRunId", Math.floorMod(System.nanoTime(), 1_000_000_000L));
            payload.put("checkRunName", "forgeops-test");
            payload.put("pullRequestNo", pullRequestNo);
            payload.put("headSha", headSha);
            payload.put("status", "completed");
            payload.put("conclusion", conclusion);
            return JsonMapper.shared().writeValueAsString(payload);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String workflowRunPayload(String conclusion) {
        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("payloadSha256", "5".repeat(64));
            payload.put("repository", "example/security-project-a");
            payload.put("action", "completed");
            payload.put("workflowName", "nightly-full");
            payload.put("workflowId", 4242);
            payload.put("branch", "main");
            payload.put("headSha", "nightly-sha");
            payload.put("status", "completed");
            payload.put("runNumber", 7);
            payload.put("conclusion", conclusion);
            return JsonMapper.shared().writeValueAsString(payload);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String deploymentPayload(long pullRequestNo, String state) {
        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("payloadSha256", "4".repeat(64));
            payload.put("repository", "example/security-project-a");
            payload.put("action", "created");
            payload.put("deploymentId", Math.floorMod(System.nanoTime(), 1_000_000_000L));
            payload.put("deploymentStatusId", Math.floorMod(System.nanoTime(), 1_000_000_000L) + 1);
            payload.put("pullRequestNo", pullRequestNo);
            payload.put("headSha", "abc123");
            payload.put("environment", "test");
            payload.put("state", state);
            return JsonMapper.shared().writeValueAsString(payload);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record Prepared(UUID feedbackId, UUID cycleId, UUID deliveryEvidenceId, long pullRequestNo) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeGitHub {
        @Bean
        @Primary
        GitHubPullRequestClient fakeGitHubPullRequests() {
            return (repository, number) -> new ObservedPullRequest(repository, number,
                    "https://github.com/" + repository + "/pull/" + number, "open", true, "forgeops/v2-ver", "abc123",
                    "main", false, null);
        }
    }
}
