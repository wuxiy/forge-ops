package com.company.forgeops.v2.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.forgeops.v2.integration.domain.IntegrationEventRepository;
import com.company.forgeops.v2.integration.domain.IntegrationEventState;
import com.company.forgeops.v2.integration.domain.OutboxEventRepository;
import com.company.forgeops.v2.integration.domain.OutboxEventState;
import com.company.forgeops.v2.integration.inbox.InboundEvent;
import com.company.forgeops.v2.integration.inbox.InboxReceipt;
import com.company.forgeops.v2.integration.inbox.IntegrationInbox;
import com.company.forgeops.v2.integration.outbox.OutboxDispatcher;
import com.company.forgeops.v2.feedback.domain.FeedbackRepository;
import com.company.forgeops.v2.agent.domain.AgentRunRepository;
import com.company.forgeops.v2.workflow.FeedbackWorkflow;
import com.company.forgeops.v2.workflow.SubmitFeedbackCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Explicitly run against a disposable PostgreSQL database; never included in a default unit-test run. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"forgeops.v2.security.token-secret=integration-test-security-secret",
                "forgeops.v2.registry.path=src/test/resources/v2-registry", "forgeops.v2.registry.workspace-root=.",
                "forgeops.v2.runtime.base-url=http://127.0.0.1:17678", "forgeops.v2.runtime.service-token=integration-runtime-token"})
class IntegrationReliabilityPostgresIT {

    @Autowired
    private IntegrationInbox inbox;

    @Autowired
    private IntegrationEventRepository inboxEvents;

    @Autowired
    private FeedbackWorkflow workflow;

    @Autowired
    private OutboxDispatcher outboxDispatcher;

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private FeedbackRepository feedbacks;

    @Autowired
    private AgentRunRepository agentRuns;

    @Test
    void missingExternalEventIdIsRejectedAndRepeatedEventIsOneDurableFact() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> inbox.accept(new InboundEvent("GIT", null, "CI_SUCCEEDED", "pilot", null, null, null, "{}", "trace")));

        String eventId = "evt-" + UUID.randomUUID();
        InboundEvent event = new InboundEvent("GIT", eventId, "CI_SUCCEEDED", "pilot", null, null, null, "{}", "trace");
        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            List<Future<InboxReceipt>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                futures.add(executor.submit(() -> inbox.accept(event)));
            }
            List<InboxReceipt> receipts = new ArrayList<>();
            for (Future<InboxReceipt> future : futures) {
                receipts.add(future.get());
            }
            UUID storedId = receipts.getFirst().eventId();
            assertTrue(receipts.stream().allMatch(receipt -> storedId.equals(receipt.eventId())));
            assertTrue(receipts.stream().allMatch(receipt -> receipt.state() == IntegrationEventState.DEFERRED));
            assertTrue(receipts.stream().anyMatch(InboxReceipt::duplicate));
            assertEquals(IntegrationEventState.DEFERRED,
                    inboxEvents.findBySourceAndExternalEventId("GIT", eventId).orElseThrow().getState());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void databaseTransactionWritesFeedbackAndOutboxTogetherAndFailedPublisherRetries() {
        String projectId = "reliability-" + UUID.randomUUID();
        long before = outboxEvents.count();
        var feedback = workflow.submit(new SubmitFeedbackCommand(projectId, "subject", "title", "description",
                "{\"safe\":true}", "e".repeat(64), 0, "outbox-trace"));

        assertEquals(before + 1, outboxEvents.count());
        var run = agentRuns.findByFeedbackId(feedback.getId()).stream().findFirst().orElseThrow();
        var outbox = outboxEvents.findAll().stream()
                .filter(event -> event.getAggregateId().equals(run.getId()))
                .findFirst().orElseThrow();
        assertTrue(outboxDispatcher.dispatch(outbox.getId()));
        outbox = outboxEvents.findById(outbox.getId()).orElseThrow();
        assertEquals(OutboxEventState.RETRYING, outbox.getState());
        assertEquals(1, outbox.getAttempts());
        assertFalse(outbox.getLastError().isBlank());
    }

    @Test
    void invalidContextRollsBackFeedbackAuditAndOutboxAsOneTransaction() {
        long feedbackBefore = feedbacks.count();
        long outboxBefore = outboxEvents.count();

        assertThrows(RuntimeException.class, () -> workflow.submit(new SubmitFeedbackCommand(
                "rollback-" + UUID.randomUUID(), "subject", "title", "description", "not-json",
                "e".repeat(64), 0, "rollback-trace")));

        assertEquals(feedbackBefore, feedbacks.count());
        assertEquals(outboxBefore, outboxEvents.count());
    }
}
