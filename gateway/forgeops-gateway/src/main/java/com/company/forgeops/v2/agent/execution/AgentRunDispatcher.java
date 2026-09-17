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
import com.company.forgeops.v2.verification.VerificationProperties;
import com.company.forgeops.v2.verification.domain.VerificationPlan;
import com.company.forgeops.v2.verification.domain.VerificationPlanRepository;
import com.company.forgeops.v2.workflow.FeedbackWorkflow;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
    private final RuntimeExecutionProperties runtimeProperties;
    private final VerificationProperties verificationProperties;
    private final VerificationPlanRepository plans;

    public AgentRunDispatcher(AgentRunRepository runs, FeedbackRepository feedbacks, ContextSnapshotRepository snapshots,
            ProjectCatalog catalog, AgentExecution execution, FeedbackWorkflow workflow, TransactionTemplate transactions,
            RuntimeExecutionProperties runtimeProperties, VerificationProperties verificationProperties,
            VerificationPlanRepository plans) {
        this.runs = runs;
        this.feedbacks = feedbacks;
        this.snapshots = snapshots;
        this.catalog = catalog;
        this.execution = execution;
        this.workflow = workflow;
        this.transactions = transactions;
        this.runtimeProperties = runtimeProperties;
        this.verificationProperties = verificationProperties;
        this.plans = plans;
    }

    public void dispatch(UUID agentRunId) {
        PreparedRun prepared = transactions.execute(status -> prepare(agentRunId));
        if (prepared == null) {
            return;
        }
        // AGT-10: every Coding attempt gets its own worktree and branch, created outside the claim transaction
        // and reused as-is when a response loss causes a redelivery of the same idempotency key.
        if (prepared.request().role() == com.company.forgeops.v2.agent.domain.AgentRole.CODING) {
            ensureCodingWorktree(prepared);
        }
        RuntimeRunSnapshot snapshot = execution.submit(prepared.request());
        if (!prepared.request().idempotencyKey().equals(snapshot.idempotencyKey())) {
            throw new RuntimeUnavailableException("Runtime returned a different idempotency key");
        }
        if (snapshot.state() == AgentRunState.RUNNING && snapshot.providerRunId() != null) {
            workflow.recordRuntimeSubmission(agentRunId, snapshot.providerRunId(), "outbox:" + agentRunId);
            return;
        }
        if (snapshot.state() == AgentRunState.QUEUED) {
            return;
        }
        if (snapshot.state() == AgentRunState.FAILED || snapshot.state() == AgentRunState.CANCELLED
                || snapshot.state() == AgentRunState.TIMED_OUT || snapshot.state() == AgentRunState.INVALID_OUTPUT) {
            workflow.recordRuntimeFailure(agentRunId, snapshot.state(), failureCategory(snapshot), "outbox:" + agentRunId);
            return;
        }
        throw new RuntimeUnavailableException("Runtime returned an incomplete submission state: " + snapshot.state());
    }

    /** Persistent AgentRun state remains QUEUED until a Runtime capacity slot is available. */
    public int dispatchQueued() {
        List<UUID> queued = runs.findByState(AgentRunState.QUEUED).stream().map(AgentRun::getId).toList();
        queued.forEach(agentRunId -> {
            try {
                dispatch(agentRunId);
            } catch (RuntimeUnavailableException unavailable) {
                // Keep the durable queue untouched; the next reconciliation cycle retries it.
            } catch (IllegalArgumentException invalidConfiguration) {
                // A removed/invalid Registry entry (or an orphaned verification attempt) cannot recover
                // by retrying; fail the workflow closed once instead of blocking the recovery loop.
                workflow.recordRuntimeFailure(agentRunId, AgentRunState.FAILED, "EXECUTION_CONFIGURATION_INVALID",
                        "reconciler:" + agentRunId);
            } catch (IllegalStateException inconsistent) {
                workflow.recordRuntimeFailure(agentRunId, AgentRunState.FAILED, "EXECUTION_STATE_INCONSISTENT",
                        "reconciler:" + agentRunId);
            }
        });
        return queued.size();
    }

    private PreparedRun prepare(UUID agentRunId) {
        AgentRun run = runs.findById(agentRunId).orElseThrow(() -> new IllegalArgumentException("Unknown agent run"));
        if (run.getState() != AgentRunState.QUEUED) {
            return null;
        }
        Feedback feedback = feedbacks.findById(run.getFeedbackId()).orElseThrow(() -> new IllegalArgumentException("Unknown feedback"));
        ResolvedProject project = catalog.require(feedback.getProjectId());
        if (run.getRole().isVerificationFamily()) {
            return prepareVerificationRun(run, feedback, project);
        }
        ContextSnapshot snapshot = snapshots.findByCycleIdOrderByCreatedAtAsc(run.getCycleId()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Agent run has no redacted context"));
        if (!project.allowsPath(project.repositoryRoot())) {
            throw new IllegalStateException("Project repository root is not Runtime allowlisted");
        }
        Path workingDir = run.getRole() == com.company.forgeops.v2.agent.domain.AgentRole.CODING
                ? codingWorktreePath(project, run.getId())
                : project.repositoryRoot();
        RuntimeRunRequest request = new RuntimeRunRequest(run.getIdempotencyKey(), project.id(), run.getRole(),
                workingDir, AgentContracts.prompt(run.getRole(), snapshot.getContentJson()),
                AgentContracts.outputSchema(run.getRole()), runtimeProperties.getRunTimeoutMillis());
        return new PreparedRun(request, project.repositoryRoot(), project.github().baseBranch());
    }

    private static Path codingWorktreePath(ResolvedProject project, UUID runId) {
        Path runsRoot = project.repositoryRoot().resolveSibling(
                project.repositoryRoot().getFileName() + "-runs");
        return runsRoot.resolve(runId.toString());
    }

    /** Creates <runsRoot>/<runId> as a git worktree on its own run branch; an existing directory is reused. */
    private void ensureCodingWorktree(PreparedRun prepared) {
        Path worktree = prepared.request().cwd();
        if (Files.isDirectory(worktree)) {
            return; // idempotent: a redelivered submission reuses the same isolated worktree
        }
        try {
            Files.createDirectories(worktree.getParent());
            String branch = "forgeops/run-" + prepared.request().idempotencyKey().replaceAll("[^A-Za-z0-9/_-]", "-");
            Process git = new ProcessBuilder("git", "-C", prepared.repositoryRoot().toString(), "worktree", "add",
                    "-b", branch, worktree.toString(), prepared.baseBranch()).redirectErrorStream(true).start();
            String output = new String(git.getInputStream().readAllBytes());
            if (!git.waitFor(60, TimeUnit.SECONDS) || git.exitValue() != 0) {
                throw new IllegalStateException("git worktree add failed: "
                        + output.substring(0, Math.min(300, output.length())));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeUnavailableException("Coding worktree creation was interrupted", interrupted);
        } catch (IOException failure) {
            throw new RuntimeUnavailableException("Coding worktree creation failed", failure);
        }
    }

    /**
     * VER-07/VER-27: verification-family runs use an isolated task directory (never the repository worktree),
     * a whitelisted read-only input document and the planner timeout. No Git credential ever reaches them.
     */
    private PreparedRun prepareVerificationRun(AgentRun run, Feedback feedback, ResolvedProject project) {
        VerificationPlan plan = plans.findByAgentRunId(run.getId()).orElse(null);
        if (plan == null) {
            // Fail this run closed: an orphaned verification attempt cannot recover by retrying.
            throw new IllegalArgumentException("Verification run has no plan: " + run.getId());
        }
        Path taskDir = verificationProperties.getExecutorTaskRoot().resolve("planner").resolve(plan.getId().toString());
        try {
            Files.createDirectories(taskDir);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot create verification task directory: " + taskDir, failure);
        }
        String input = "{\"prUrl\":\"" + plan.getPrUrl() + "\",\"headSha\":\"" + plan.getPrHeadSha()
                + "\",\"changedFiles\":" + plan.getImpactFiles()
                + ",\"requiredCategories\":" + plan.getRequiredCategories()
                + ",\"selectionMode\":\"" + plan.getSelectionMode() + "\"}";
        int timeout = (int) Math.min(verificationProperties.getPlannerTimeout().toMillis(),
                runtimeProperties.getRunTimeoutMillis());
        RuntimeRunRequest request = new RuntimeRunRequest(run.getIdempotencyKey(), project.id(), run.getRole(),
                taskDir, AgentContracts.prompt(run.getRole(), input), AgentContracts.outputSchema(run.getRole()),
                timeout);
        return new PreparedRun(request, project.repositoryRoot(), project.github().baseBranch());
    }

    private static String failureCategory(RuntimeRunSnapshot snapshot) {
        return snapshot.failureCategory() == null || snapshot.failureCategory().isBlank()
                ? "RUNTIME_" + snapshot.state() : snapshot.failureCategory();
    }

    private record PreparedRun(RuntimeRunRequest request, Path repositoryRoot, String baseBranch) {
    }
}
