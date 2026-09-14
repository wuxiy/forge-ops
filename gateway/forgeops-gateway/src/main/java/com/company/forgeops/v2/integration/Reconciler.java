package com.company.forgeops.v2.integration;

import com.company.forgeops.v2.integration.inbox.IntegrationInbox;
import com.company.forgeops.v2.integration.outbox.OutboxDispatcher;
import com.company.forgeops.v2.agent.execution.AgentRunMonitor;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Explicit recovery entrypoint. Scheduling is wired only after deployment observability exists. */
@Service
public class Reconciler {

    private final IntegrationInbox inbox;
    private final OutboxDispatcher outbox;
    private final AgentRunMonitor agentRuns;

    public Reconciler(IntegrationInbox inbox, OutboxDispatcher outbox, AgentRunMonitor agentRuns) {
        this.inbox = inbox;
        this.outbox = outbox;
        this.agentRuns = agentRuns;
    }

    public ReconciliationReport reconcile() {
        int recoveredOutbox = outbox.recoverAbandonedDispatches(Duration.ofMinutes(5));
        int replayedInbox = inbox.reconcileDeferred();
        int dispatchedOutbox = outbox.dispatchDue();
        int inspectedAgentRuns = agentRuns.inspectRunning();
        return new ReconciliationReport(recoveredOutbox, replayedInbox, dispatchedOutbox, inspectedAgentRuns);
    }

    @Scheduled(fixedDelayString = "${forgeops.v2.reconciliation.fixed-delay-ms:5000}")
    public void scheduledReconcile() {
        reconcile();
    }

    public record ReconciliationReport(int recoveredOutbox, int replayedInbox, int dispatchedOutbox, int inspectedAgentRuns) {
    }
}
