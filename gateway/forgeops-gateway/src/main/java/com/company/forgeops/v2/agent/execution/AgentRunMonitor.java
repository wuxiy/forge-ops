package com.company.forgeops.v2.agent.execution;

import com.company.forgeops.v2.agent.domain.AgentRole;
import com.company.forgeops.v2.agent.domain.AgentRun;
import com.company.forgeops.v2.agent.domain.AgentRunRepository;
import com.company.forgeops.v2.agent.domain.AgentRunState;
import com.company.forgeops.v2.workflow.FeedbackWorkflow;
import com.company.forgeops.v2.verification.DeliveryEvidenceService;
import com.company.forgeops.v2.verification.VerificationService;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/** Reconciles active Runtime facts. A transient Runtime outage leaves the durable run active for a later retry. */
@Service
public class AgentRunMonitor {

    private final AgentRunRepository runs;
    private final AgentExecution execution;
    private final FeedbackWorkflow workflow;
    private final DeliveryEvidenceService deliveryEvidence;
    private final VerificationService verification;
    private final ObjectMapper json;

    public AgentRunMonitor(AgentRunRepository runs, AgentExecution execution, FeedbackWorkflow workflow,
            DeliveryEvidenceService deliveryEvidence, VerificationService verification, ObjectMapper json) {
        this.runs = runs;
        this.execution = execution;
        this.workflow = workflow;
        this.deliveryEvidence = deliveryEvidence;
        this.verification = verification;
        this.json = json;
    }

    public int inspectRunning() {
        List<AgentRun> active = runs.findByState(AgentRunState.RUNNING);
        active.forEach(this::inspect);
        return active.size();
    }

    private void inspect(AgentRun run) {
        RuntimeRunSnapshot snapshot;
        try {
            snapshot = execution.inspect(run.getIdempotencyKey());
        } catch (RuntimeUnavailableException unavailable) {
            return;
        }
        if (!run.getIdempotencyKey().equals(snapshot.idempotencyKey()) || snapshot.state() == AgentRunState.RUNNING) {
            return;
        }
        if (snapshot.state() == AgentRunState.SUCCEEDED && snapshot.resultJson() == null) {
            workflow.recordRuntimeFailure(run.getId(), AgentRunState.INVALID_OUTPUT, failureCategory(snapshot), "monitor:" + run.getId());
            return;
        }
        if (snapshot.state() != AgentRunState.SUCCEEDED) {
            if (run.getRole().isVerificationFamily()) {
                workflow.recordRuntimeFailure(run.getId(), snapshot.state(), failureCategory(snapshot), "monitor:" + run.getId());
                verification.onPlannerFailure(run.getId(), snapshot.state(), failureCategory(snapshot),
                        "monitor:" + run.getId());
                return;
            }
            workflow.recordRuntimeFailure(run.getId(), snapshot.state(), failureCategory(snapshot), "monitor:" + run.getId());
            return;
        }
        if (run.getRole() == AgentRole.VERIFICATION) {
            try {
                var result = AgentContracts.parseVerificationPlan(json, snapshot.resultJson());
                workflow.recordVerificationPlannerOutput(run.getId(),
                        AgentContracts.canonicalVerificationPlanJson(json, result), "monitor:" + run.getId());
                verification.onPlannerResult(run.getId(), result, snapshot.resultJson(), "monitor:" + run.getId());
            } catch (IllegalArgumentException invalid) {
                workflow.recordRuntimeFailure(run.getId(), AgentRunState.INVALID_OUTPUT, "INVALID_PLAN_OUTPUT",
                        "monitor:" + run.getId());
                verification.onPlannerFailure(run.getId(), AgentRunState.INVALID_OUTPUT, "INVALID_PLAN_OUTPUT",
                        "monitor:" + run.getId());
            }
            return;
        }
        if (run.getRole() == AgentRole.FAILURE_ANALYSIS) {
            try {
                var result = AgentContracts.parseFailureTriage(json, snapshot.resultJson());
                workflow.recordVerificationPlannerOutput(run.getId(),
                        AgentContracts.canonicalFailureTriageJson(json, result), "monitor:" + run.getId());
            } catch (IllegalArgumentException invalid) {
                workflow.recordRuntimeFailure(run.getId(), AgentRunState.INVALID_OUTPUT, "INVALID_FAILURE_TRIAGE_OUTPUT",
                        "monitor:" + run.getId());
            }
            return;
        }
        if (run.getRole() == AgentRole.CODING) {
            try {
                var result = AgentContracts.parseCoding(json, snapshot.resultJson());
                if (result.outcome() != AgentContracts.CodingOutcome.PR_CREATED) {
                    workflow.recordRuntimeFailure(run.getId(), AgentRunState.FAILED, "CODING_" + result.outcome(),
                            "monitor:" + run.getId());
                    return;
                }
                deliveryEvidence.registerCodingPrDeclaration(run.getId(), result,
                        AgentContracts.canonicalCodingJson(json, result), "monitor:" + run.getId());
            } catch (IllegalArgumentException invalid) {
                workflow.recordRuntimeFailure(run.getId(), AgentRunState.INVALID_OUTPUT, "INVALID_CODING_OUTPUT",
                        "monitor:" + run.getId());
                return;
            }
            return;
        }
        try {
            var result = AgentContracts.parseTriage(json, snapshot.resultJson());
            workflow.recordTriageSuccess(run.getId(), result.decision(), AgentContracts.canonicalTriageJson(json, result),
                    "monitor:" + run.getId());
        } catch (IllegalArgumentException invalid) {
            workflow.recordRuntimeFailure(run.getId(), AgentRunState.INVALID_OUTPUT, "INVALID_TRIAGE_OUTPUT",
                    "monitor:" + run.getId());
        }
    }

    private static String failureCategory(RuntimeRunSnapshot snapshot) {
        if (snapshot.resultError() != null && !snapshot.resultError().isBlank()) return snapshot.resultError();
        if (snapshot.failureCategory() != null && !snapshot.failureCategory().isBlank()) return snapshot.failureCategory();
        return "RUNTIME_" + snapshot.state();
    }
}
