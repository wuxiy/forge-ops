package com.company.forgeops.v2.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.company.forgeops.GatewayApplication;
import com.company.forgeops.v2.agent.domain.AgentRole;
import com.company.forgeops.v2.agent.domain.AgentRunRepository;
import com.company.forgeops.v2.agent.execution.AgentContracts;
import com.company.forgeops.v2.agent.execution.AgentContracts.CodingOutcome;
import com.company.forgeops.v2.agent.execution.AgentContracts.CodingResult;
import com.company.forgeops.v2.agent.execution.AgentContracts.TriageDecision;
import com.company.forgeops.v2.agent.execution.AgentContracts.TriageResult;
import com.company.forgeops.v2.feedback.domain.FeedbackRepository;
import com.company.forgeops.v2.integration.github.GitHubPullRequestClient;
import com.company.forgeops.v2.integration.github.GitHubPullRequestClient.ObservedPullRequest;
import com.company.forgeops.v2.integration.domain.IntegrationEventState;
import com.company.forgeops.v2.integration.inbox.InboundEvent;
import com.company.forgeops.v2.integration.inbox.IntegrationInbox;
import com.company.forgeops.v2.verification.domain.DeliveryEvidenceRepository;
import com.company.forgeops.v2.verification.domain.DeliveryEvidenceState;
import com.company.forgeops.v2.verification.domain.DeliveryEvidence;
import com.company.forgeops.v2.workflow.FeedbackState;
import com.company.forgeops.v2.workflow.FeedbackWorkflow;
import com.company.forgeops.v2.workflow.SubmitFeedbackCommand;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

/** Explicit PostgreSQL proof that an Agent claim cannot enter PR_READY without an independently returned PR fact. */
@SpringBootTest(classes = {GatewayApplication.class, DeliveryEvidencePostgresIT.FakeGitHub.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"forgeops.v2.security.token-secret=integration-test-security-secret",
                "forgeops.v2.registry.path=src/test/resources/v2-registry", "forgeops.v2.registry.workspace-root=.",
                "forgeops.v2.runtime.base-url=http://127.0.0.1:17678", "forgeops.v2.runtime.service-token=integration-runtime-token",
                "forgeops.v2.github.enabled=true", "forgeops.v2.github.webhook-secret=integration-webhook-secret",
                "forgeops.v2.github.api-token=integration-read-only-token",
                "forgeops.v2.verification.planner-enabled=false"})
class DeliveryEvidencePostgresIT {

    @Autowired
    private FeedbackWorkflow workflow;

    @Autowired
    private AgentRunRepository agentRuns;

    @Autowired
    private DeliveryEvidenceService evidence;

    @Autowired
    private DeliveryEvidenceVerifier verifier;

    @Autowired
    private IntegrationInbox inbox;

    @Autowired
    private DeliveryEvidenceRepository evidenceRepository;

    @Autowired
    private FeedbackRepository feedbacks;

    @Test
    void independentlyMatchedDraftPrThenExactCiAndTestDeploymentFactsReachWaitingVerify() {
        long pullRequestNo = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000L) + 1;
        var feedback = workflow.submit(new SubmitFeedbackCommand("security-project-a", "reporter", "title", "description",
                "{\"safe\":true}", "a".repeat(64), 0, "delivery-evidence-it"));
        var triage = agentRuns.findByFeedbackId(feedback.getId()).getFirst();
        workflow.recordRuntimeSubmission(triage.getId(), "triage-provider-" + UUID.randomUUID(), "delivery-evidence-it");
        var triageResult = new TriageResult(TriageDecision.PROCEED_CODING, "summary", "root cause", List.of(), List.of(),
                List.of(), List.of(), List.of("plan"));
        workflow.recordTriageSuccess(triage.getId(), triageResult.decision(),
                AgentContracts.canonicalTriageJson(JsonMapper.shared(), triageResult), "delivery-evidence-it");
        var coding = agentRuns.findByFeedbackId(feedback.getId()).stream()
                .filter(run -> run.getRole() == AgentRole.CODING).findFirst().orElseThrow();
        workflow.recordRuntimeSubmission(coding.getId(), "coding-provider-" + UUID.randomUUID(), "delivery-evidence-it");
        var declaration = new CodingResult(CodingOutcome.PR_CREATED, "forgeops/v2-42", "abc123",
                "https://github.com/example/security-project-a/pull/" + pullRequestNo, List.of("src/App.java"),
                List.of("mvn test"), List.of(), null, null);
        evidence.registerCodingPrDeclaration(coding.getId(), declaration,
                AgentContracts.canonicalCodingJson(JsonMapper.shared(), declaration), "delivery-evidence-it");

        assertEquals(FeedbackState.CODE_RUNNING, feedbacks.findById(feedback.getId()).orElseThrow().getState());
        assertEquals(1, verifier.reconcilePending());
        assertEquals(DeliveryEvidenceState.VERIFIED, evidenceRepository.findByAgentRunId(coding.getId()).orElseThrow().getState());
        // The verified PR fact opens the verification plan: the deterministic fallback runs immediately.
        assertEquals(FeedbackState.VERIFY_RUNNING, feedbacks.findById(feedback.getId()).orElseThrow().getState());

        // VER-02: a check for another head SHA is stale and cannot gate; the plan stays open.
        var wrongSha = inbox.accept(new InboundEvent("GITHUB", "ci-wrong-" + feedback.getId(), "CHECK_RUN",
                "security-project-a", null, null, null, checkRunPayload(pullRequestNo, "wrong-sha", "success", "e1"),
                "delivery-evidence-it"));
        assertEquals(IntegrationEventState.DEFERRED, wrongSha.state());
        assertEquals(FeedbackState.VERIFY_RUNNING, feedbacks.findById(feedback.getId()).orElseThrow().getState());

        // The mapped PR check supplies BUILD evidence; the gate passes deterministically.
        var gateCheck = inbox.accept(new InboundEvent("GITHUB", "ci-gate-" + feedback.getId(), "CHECK_RUN",
                "security-project-a", null, null, null, checkRunPayload(pullRequestNo, "abc123", "success", "e2"),
                "delivery-evidence-it"));
        assertEquals(IntegrationEventState.APPLIED, gateCheck.state());
        assertEquals(FeedbackState.GATE_PASS, feedbacks.findById(feedback.getId()).orElseThrow().getState());

        var receipt = inbox.accept(new InboundEvent("GITHUB", "merge-" + feedback.getId(), "PULL_REQUEST",
                "security-project-a", null, null, null, mergePayload(pullRequestNo), "delivery-evidence-it"));
        assertEquals(IntegrationEventState.APPLIED, receipt.state());
        assertEquals(FeedbackState.BUILD_RUNNING, feedbacks.findById(feedback.getId()).orElseThrow().getState());

        var failedBuild = inbox.accept(new InboundEvent("GITHUB", "ci-failed-" + feedback.getId(), "CHECK_RUN",
                "security-project-a", null, null, null, checkRunPayload(pullRequestNo, "abc123", "failure", "e3"),
                "delivery-evidence-it"));
        assertEquals(IntegrationEventState.APPLIED, failedBuild.state());
        assertEquals(FeedbackState.BUILD_FAILED, feedbacks.findById(feedback.getId()).orElseThrow().getState());

        var retriedBuild = inbox.accept(new InboundEvent("GITHUB", "ci-success-" + feedback.getId(), "CHECK_RUN",
                "security-project-a", null, null, null, checkRunPayload(pullRequestNo, "abc123", "success", "e4"),
                "delivery-evidence-it"));
        assertEquals(IntegrationEventState.APPLIED, retriedBuild.state());
        assertEquals(FeedbackState.DEPLOY_RUNNING, feedbacks.findById(feedback.getId()).orElseThrow().getState());

        var failedDeploy = inbox.accept(new InboundEvent("GITHUB", "deploy-failed-" + feedback.getId(), "DEPLOYMENT_STATUS",
                "security-project-a", null, null, null, deploymentPayload(pullRequestNo, "failure"),
                "delivery-evidence-it"));
        assertEquals(IntegrationEventState.APPLIED, failedDeploy.state());
        assertEquals(FeedbackState.DEPLOY_FAILED, feedbacks.findById(feedback.getId()).orElseThrow().getState());

        var retriedDeploy = inbox.accept(new InboundEvent("GITHUB", "deploy-success-" + feedback.getId(), "DEPLOYMENT_STATUS",
                "security-project-a", null, null, null, deploymentPayload(pullRequestNo, "success"),
                "delivery-evidence-it"));
        assertEquals(IntegrationEventState.APPLIED, retriedDeploy.state());
        assertEquals(FeedbackState.WAITING_VERIFY, feedbacks.findById(feedback.getId()).orElseThrow().getState());
    }

    @Test
    void databaseRejectsBindingOneGitHubPrToTwoDifferentFeedbackCycles() {
        long pullRequestNo = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000L) + 1;
        var firstFeedback = workflow.submit(new SubmitFeedbackCommand("security-project-a", "reporter-a", "title", "description",
                "{\"safe\":true}", "b".repeat(64), 0, "delivery-unique-it"));
        var secondFeedback = workflow.submit(new SubmitFeedbackCommand("security-project-a", "reporter-b", "title", "description",
                "{\"safe\":true}", "c".repeat(64), 0, "delivery-unique-it"));
        var firstRun = agentRuns.findByFeedbackId(firstFeedback.getId()).getFirst();
        var secondRun = agentRuns.findByFeedbackId(secondFeedback.getId()).getFirst();
        evidenceRepository.saveAndFlush(DeliveryEvidence.pending(firstFeedback.getId(), firstFeedback.getCurrentCycleId(),
                firstRun.getId(), "example/security-project-a", pullRequestNo, "main", "forgeops/v2-unique", "sha-one",
                "https://github.com/example/security-project-a/pull/" + pullRequestNo));

        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> evidenceRepository.saveAndFlush(DeliveryEvidence.pending(secondFeedback.getId(), secondFeedback.getCurrentCycleId(),
                        secondRun.getId(), "example/security-project-a", pullRequestNo, "main", "forgeops/v2-unique", "sha-two",
                        "https://github.com/example/security-project-a/pull/" + pullRequestNo)));
    }

    @Test
    void staleEvidenceIsRejectedWithoutChangingAnUnrelatedFeedbackState() {
        long pullRequestNo = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000L) + 1;
        var feedback = workflow.submit(new SubmitFeedbackCommand("security-project-a", "reporter", "title", "description",
                "{\"safe\":true}", "d".repeat(64), 0, "delivery-stale-it"));
        var triageRun = agentRuns.findByFeedbackId(feedback.getId()).getFirst();
        var stale = evidenceRepository.saveAndFlush(DeliveryEvidence.pending(feedback.getId(), feedback.getCurrentCycleId(),
                triageRun.getId(), "example/security-project-a", pullRequestNo, "main", "forgeops/v2-stale", "sha-stale",
                "https://github.com/example/security-project-a/pull/" + pullRequestNo));

        verifier.reconcilePending();

        assertEquals(DeliveryEvidenceState.REJECTED, evidenceRepository.findById(stale.getId()).orElseThrow().getState());
        assertEquals(FeedbackState.TRIAGE_QUEUED, feedbacks.findById(feedback.getId()).orElseThrow().getState());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeGitHub {
        @Bean
        @Primary
        GitHubPullRequestClient fakeGitHubPullRequests() {
            return (repository, number) -> new ObservedPullRequest(repository, number,
                    "https://github.com/" + repository + "/pull/" + number, "open", true, "forgeops/v2-42", "abc123",
                    "main", false, null);
        }
    }

    private static String mergePayload(long pullRequestNo) {
        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("payloadSha256", "a".repeat(64));
            payload.put("repository", "example/security-project-a");
            payload.put("action", "closed");
            payload.put("number", pullRequestNo);
            payload.put("baseBranch", "main");
            payload.put("headBranch", "forgeops/v2-42");
            payload.put("headSha", "abc123");
            payload.put("draft", false);
            payload.put("merged", true);
            payload.put("mergedBy", "forgeops-test-owner");
            return JsonMapper.shared().writeValueAsString(payload);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String checkRunPayload(long pullRequestNo, String headSha, String conclusion, String digestTag) {
        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("payloadSha256", "b".repeat(62) + digestTag);
            payload.put("repository", "example/security-project-a");
            payload.put("action", "completed");
            payload.put("checkRunId", 77);
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

    private static String deploymentPayload(long pullRequestNo, String state) {
        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("payloadSha256", "c".repeat(64));
            payload.put("repository", "example/security-project-a");
            payload.put("action", "created");
            payload.put("deploymentId", 90);
            payload.put("deploymentStatusId", 91);
            payload.put("pullRequestNo", pullRequestNo);
            payload.put("headSha", "abc123");
            payload.put("environment", "test");
            payload.put("state", state);
            return JsonMapper.shared().writeValueAsString(payload);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
