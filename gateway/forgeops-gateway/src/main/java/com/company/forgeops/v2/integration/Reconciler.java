package com.company.forgeops.v2.integration;

import com.company.forgeops.v2.integration.inbox.IntegrationInbox;
import com.company.forgeops.v2.integration.outbox.OutboxDispatcher;
import com.company.forgeops.v2.agent.execution.AgentRunMonitor;
import com.company.forgeops.v2.agent.execution.AgentRunDispatcher;
import com.company.forgeops.v2.verification.DeliveryEvidenceVerifier;
import com.company.forgeops.v2.verification.VerificationService;
import com.company.forgeops.v2.observability.ForgeOpsMetrics;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Explicit recovery entrypoint. Scheduling is wired only after deployment observability exists. */
@Service
public class Reconciler {

    private final IntegrationInbox inbox;
    private final OutboxDispatcher outbox;
    private final AgentRunMonitor agentRuns;
    private final AgentRunDispatcher agentDispatcher;
    private final DeliveryEvidenceVerifier deliveryEvidence;
    private final VerificationService verification;
    private final ForgeOpsMetrics metrics;
    private final long abandonedDispatchThresholdSeconds;

    public Reconciler(IntegrationInbox inbox, OutboxDispatcher outbox, AgentRunMonitor agentRuns,
            AgentRunDispatcher agentDispatcher, DeliveryEvidenceVerifier deliveryEvidence,
            VerificationService verification, ForgeOpsMetrics metrics,
            @org.springframework.beans.factory.annotation.Value(
                    "${forgeops.v2.reconciliation.abandoned-threshold-seconds:300}")
            long abandonedDispatchThresholdSeconds) {
        this.inbox = inbox;
        this.outbox = outbox;
        this.agentRuns = agentRuns;
        this.agentDispatcher = agentDispatcher;
        this.deliveryEvidence = deliveryEvidence;
        this.verification = verification;
        this.metrics = metrics;
        this.abandonedDispatchThresholdSeconds = abandonedDispatchThresholdSeconds;
    }

    public ReconciliationReport reconcile() {
        metrics.reconciliationCycle();
        int recoveredOutbox = outbox.recoverAbandonedDispatches(
                Duration.ofSeconds(abandonedDispatchThresholdSeconds));
        int replayedInbox = inbox.reconcileDeferred();
        int dispatchedOutbox = outbox.dispatchDue();
        int inspectedAgentRuns = agentRuns.inspectRunning();
        int dispatchedQueuedAgentRuns = agentDispatcher.dispatchQueued();
        int verifiedDeliveryEvidence = deliveryEvidence.reconcilePending();
        int recoveredStuckPlans = verification.reconcileStuckPlans();
        int purgedEvidence = verification.purgeExpiredEvidence();
        return new ReconciliationReport(recoveredOutbox, replayedInbox, dispatchedOutbox, inspectedAgentRuns,
                dispatchedQueuedAgentRuns, verifiedDeliveryEvidence, recoveredStuckPlans, purgedEvidence);
    }

    @Scheduled(fixedDelayString = "${forgeops.v2.reconciliation.fixed-delay-ms:5000}")
    public void scheduledReconcile() {
        reconcile();
    }

    public record ReconciliationReport(int recoveredOutbox, int replayedInbox, int dispatchedOutbox, int inspectedAgentRuns,
            int dispatchedQueuedAgentRuns, int verifiedDeliveryEvidence, int recoveredStuckPlans, int purgedEvidence) {
    }
}
