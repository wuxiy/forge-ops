package com.company.forgeops.v2.feedback.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.company.forgeops.v2.workflow.FeedbackState;
import com.company.forgeops.v2.workflow.WorkflowConflictException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FeedbackCycleTest {

    @Test
    void reopenCreatesANewCycleWithoutChangingThePriorCycle() {
        UUID feedbackId = UUID.randomUUID();
        FeedbackCycle first = FeedbackCycle.initial(feedbackId, "user-a");
        FeedbackCycle reopened = FeedbackCycle.reopen(feedbackId, 2, "user-a", "still broken");

        assertEquals(1, first.getCycleNo());
        assertEquals(2, reopened.getCycleNo());
        assertEquals(feedbackId, first.getFeedbackId());
        assertEquals(feedbackId, reopened.getFeedbackId());
        assertNotEquals(first.getId(), reopened.getId());
        assertEquals("still broken", reopened.getReopenReason());
    }

    @Test
    void illegalStateTransitionDoesNotSilentlyChangeState() {
        UUID cycleId = UUID.randomUUID();
        Feedback feedback = Feedback.create(UUID.randomUUID(), "pilot-a", 1001, "user-a", "title", "description", cycleId);

        assertThrows(WorkflowConflictException.class, () -> feedback.transitionTo(FeedbackState.DONE));
        assertEquals(FeedbackState.RECEIVED, feedback.getState());
    }

    @Test
    void reopenMustUseANewCycleNumber() {
        assertThrows(IllegalArgumentException.class,
                () -> FeedbackCycle.reopen(UUID.randomUUID(), 1, "user-a", "not valid"));
    }
}
