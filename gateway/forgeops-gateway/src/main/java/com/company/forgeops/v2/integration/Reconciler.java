package com.company.forgeops.v2.integration;

import com.company.forgeops.v2.integration.inbox.IntegrationInbox;
import com.company.forgeops.v2.integration.outbox.OutboxDispatcher;
import java.time.Duration;
import org.springframework.stereotype.Service;

/** Explicit recovery entrypoint. Scheduling is wired only after deployment observability exists. */
@Service
public class Reconciler {

    private final IntegrationInbox inbox;
    private final OutboxDispatcher outbox;

    public Reconciler(IntegrationInbox inbox, OutboxDispatcher outbox) {
        this.inbox = inbox;
        this.outbox = outbox;
    }

    public ReconciliationReport reconcile() {
        int recoveredOutbox = outbox.recoverAbandonedDispatches(Duration.ofMinutes(5));
        int replayedInbox = inbox.reconcileDeferred();
        int dispatchedOutbox = outbox.dispatchDue();
        return new ReconciliationReport(recoveredOutbox, replayedInbox, dispatchedOutbox);
    }

    public record ReconciliationReport(int recoveredOutbox, int replayedInbox, int dispatchedOutbox) {
    }
}
