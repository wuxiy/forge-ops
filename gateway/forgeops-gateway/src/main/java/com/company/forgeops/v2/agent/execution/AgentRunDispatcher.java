package com.company.forgeops.v2.agent.execution;

import com.company.forgeops.v2.agent.domain.AgentRun;
import com.company.forgeops.v2.agent.domain.AgentRunRepository;
import com.company.forgeops.v2.agent.domain.AgentRunState;
import com.company.forgeops.v2.feedback.domain.ContextSnapshot;
import com.company.forgeops.v2.feedback.domain.ContextSnapshotRepository;
import com.company.forgeops.v2.feedback.domain.Feedback;
import com.company.forgeops.v2.feedback.domain.FeedbackRepository;
import com.company.forgeops.v2.registry.ProjectCatalog;
import com.company.forgeops.v2.registry.ResolvedProject;
import com.company.forgeops.v2.workflow.FeedbackWorkflow;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Claims a durable AgentRun fact, calls Runtime outside the transaction, then asks Workflow to record the result. */
@Service
public class AgentRunDispatcher {

    private final AgentRunRepository runs;
    private final FeedbackRepository feedbacks;
    private final ContextSnapshotRepository snapshots;
    private final ProjectCatalog catalog;
    private final AgentExecution execution;
    private final FeedbackWorkflow workflow;
    private final TransactionTemplate transactions;

    public AgentRunDispatcher(AgentRunRepository runs, FeedbackRepository feedbacks, ContextSnapshotRepository snapshots,
            ProjectCatalog catalog, AgentExecution execution, FeedbackWorkflow workflow, TransactionTemplate transactions) {
        this.runs = runs;
        this.feedbacks = feedbacks;
        this.snapshots = snapshots;
        this.catalog = catalog;
        this.execution = execution;
        this.workflow = workflow;
        this.transactions = transactions;
    }

    public void dispatch(UUID agentRunId) {
        PreparedRun prepared = transactions.execute(status -> prepare(agentRunId));
        if (prepared == null) {
            return;
        }
        RuntimeRunSnapshot snapshot = execution.submit(prepared.request());
        if (!prepared.request().idempotencyKey().equals(snapshot.idempotencyKey())) {
            throw new RuntimeUnavailableException("Runtime returned a different idempotency key");
        }
        if (snapshot.state() == AgentRunState.RUNNING && snapshot.providerRunId() != null) {
            workflow.recordRuntimeSubmission(agentRunId, snapshot.providerRunId(), "outbox:" + agentRunId);
            return;
        }
        if (snapshot.state() == AgentRunState.FAILED || snapshot.state() == AgentRunState.CANCELLED
                || snapshot.state() == AgentRunState.TIMED_OUT || snapshot.state() == AgentRunState.INVALID_OUTPUT) {
            workflow.recordRuntimeFailure(agentRunId, snapshot.state(), failureCategory(snapshot), "outbox:" + agentRunId);
            return;
        }
        throw new RuntimeUnavailableException("Runtime returned an incomplete submission state: " + snapshot.state());
    }

    private PreparedRun prepare(UUID agentRunId) {
        AgentRun run = runs.findById(agentRunId).orElseThrow(() -> new IllegalArgumentException("Unknown agent run"));
        if (run.getState() != AgentRunState.QUEUED) {
            return null;
        }
        Feedback feedback = feedbacks.findById(run.getFeedbackId()).orElseThrow(() -> new IllegalArgumentException("Unknown feedback"));
        ContextSnapshot snapshot = snapshots.findByCycleIdOrderByCreatedAtAsc(run.getCycleId()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Agent run has no redacted context"));
        ResolvedProject project = catalog.require(feedback.getProjectId());
        if (!project.allowsPath(project.repositoryRoot())) {
            throw new IllegalStateException("Project repository root is not Runtime allowlisted");
        }
        RuntimeRunRequest request = new RuntimeRunRequest(run.getIdempotencyKey(), project.id(), run.getRole(),
                project.repositoryRoot(), AgentContracts.prompt(run.getRole(), snapshot.getContentJson()),
                AgentContracts.outputSchema(run.getRole()));
        return new PreparedRun(request);
    }

    private static String failureCategory(RuntimeRunSnapshot snapshot) {
        return snapshot.failureCategory() == null || snapshot.failureCategory().isBlank()
                ? "RUNTIME_" + snapshot.state() : snapshot.failureCategory();
    }

    private record PreparedRun(RuntimeRunRequest request) {
    }
}
