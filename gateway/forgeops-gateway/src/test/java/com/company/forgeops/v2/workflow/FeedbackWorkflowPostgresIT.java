package com.company.forgeops.v2.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.company.forgeops.v2.feedback.domain.ContextSnapshotRepository;
import com.company.forgeops.v2.feedback.domain.FeedbackCycleRepository;
import com.company.forgeops.v2.feedback.domain.FeedbackRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Invoked explicitly against a fresh PostgreSQL database. It is intentionally named IT so
 * ordinary unit-test runs cannot accidentally use a configured non-test datasource.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"forgeops.v2.security.token-secret=integration-test-security-secret",
                "forgeops.v2.registry.path=src/test/resources/v2-registry", "forgeops.v2.registry.workspace-root=."})
class FeedbackWorkflowPostgresIT {

    @Autowired
    private FeedbackWorkflow workflow;

    @Autowired
    private FeedbackRepository feedbacks;

    @Autowired
    private FeedbackCycleRepository cycles;

    @Autowired
    private ContextSnapshotRepository snapshots;

    @Test
    void createsCycleOneAndReopenCreatesAnIndependentCycleTwo() {
        String projectId = "postgres-it-" + UUID.randomUUID();
        var feedback = workflow.submit(new SubmitFeedbackCommand(projectId, "subject-a", "The task", "It fails",
                "{\"safe\":true}", "a".repeat(64), 0, "it-trace-1"));
        UUID firstCycleId = feedback.getCurrentCycleId();
        assertEquals(FeedbackState.CONTEXT_READY, feedback.getState());
        assertEquals(FeedbackState.CONTEXT_READY, feedbacks.findById(feedback.getId()).orElseThrow().getState());

        feedback = advanceToWaitingVerify(feedback, "it-first");
        feedback = workflow.reopen(feedback.getId(), "subject-a", "Still failing", "{\"safe\":true}", "b".repeat(64), 0,
                "it-reopen-1");
        UUID secondCycleId = feedback.getCurrentCycleId();
        feedback = advanceToWaitingVerify(feedback, "it-second");
        feedback = workflow.reopen(feedback.getId(), "subject-a", "Still failing again", "{\"safe\":true}", "c".repeat(64),
                0, "it-reopen-2");

        assertEquals(FeedbackState.CONTEXT_READY, feedback.getState());
        assertNotEquals(firstCycleId, feedback.getCurrentCycleId());
        assertEquals(3, cycles.findMaxCycleNo(feedback.getId()));
        assertEquals(1, cycles.findByFeedbackIdAndCycleNo(feedback.getId(), 1).orElseThrow().getCycleNo());
        assertEquals(2, cycles.findByFeedbackIdAndCycleNo(feedback.getId(), 2).orElseThrow().getCycleNo());
        assertEquals(3, cycles.findByFeedbackIdAndCycleNo(feedback.getId(), 3).orElseThrow().getCycleNo());
        assertEquals(1, snapshots.findByCycleIdOrderByCreatedAtAsc(firstCycleId).size());
        assertEquals(1, snapshots.findByCycleIdOrderByCreatedAtAsc(secondCycleId).size());
        assertEquals(1, snapshots.findByCycleIdOrderByCreatedAtAsc(feedback.getCurrentCycleId()).size());
        assertEquals(feedback.getCurrentCycleId(), feedbacks.findById(feedback.getId()).orElseThrow().getCurrentCycleId());
    }

    @Test
    void assignsOneContinuousProjectSequenceUnderOneHundredConcurrentSubmissions() throws Exception {
        String projectId = "postgres-concurrent-" + UUID.randomUUID();
        workflow.submit(new SubmitFeedbackCommand(projectId, "seed", "seed", "seed", "{\"safe\":true}",
                "c".repeat(64), 0, "it-seed"));

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                int sample = i;
                futures.add(executor.submit(() -> workflow.submit(new SubmitFeedbackCommand(projectId, "subject-" + sample,
                        "title-" + sample, "description", "{\"safe\":true}", "d".repeat(64), 0,
                        "it-concurrent-" + sample)).getDisplayNo()));
            }
            Set<Long> displayNumbers = new HashSet<>();
            for (Future<Long> future : futures) {
                displayNumbers.add(future.get());
            }

            assertEquals(100, displayNumbers.size());
            for (long expected = 1002; expected <= 1101; expected++) {
                org.junit.jupiter.api.Assertions.assertTrue(displayNumbers.contains(expected), "missing " + expected);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private com.company.forgeops.v2.feedback.domain.Feedback advanceToWaitingVerify(
            com.company.forgeops.v2.feedback.domain.Feedback feedback, String tracePrefix) {
        feedback = workflow.transition(feedback.getId(), FeedbackState.TRIAGE_QUEUED, "system", tracePrefix + "-1");
        feedback = workflow.transition(feedback.getId(), FeedbackState.TRIAGE_RUNNING, "system", tracePrefix + "-2");
        feedback = workflow.transition(feedback.getId(), FeedbackState.CODE_QUEUED, "system", tracePrefix + "-3");
        feedback = workflow.transition(feedback.getId(), FeedbackState.CODE_RUNNING, "system", tracePrefix + "-4");
        feedback = workflow.transition(feedback.getId(), FeedbackState.PR_READY, "system", tracePrefix + "-5");
        feedback = workflow.transition(feedback.getId(), FeedbackState.BUILD_RUNNING, "system", tracePrefix + "-6");
        feedback = workflow.transition(feedback.getId(), FeedbackState.DEPLOY_RUNNING, "system", tracePrefix + "-7");
        return workflow.transition(feedback.getId(), FeedbackState.WAITING_VERIFY, "system", tracePrefix + "-8");
    }
}
