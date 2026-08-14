package com.company.forgeops.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.forgeops.feedback.domain.FeedbackStatus;
import org.junit.jupiter.api.Test;

class FeedbackStatusTest {

    @Test
    void mainPathFollowsArchitectureFlow() {
        FeedbackStatus[] happyPath = {
                FeedbackStatus.SUBMITTED, FeedbackStatus.CONTEXT_BUILDING, FeedbackStatus.TRIAGING,
                FeedbackStatus.CODING, FeedbackStatus.PR_REVIEW, FeedbackStatus.BUILDING,
                FeedbackStatus.DEPLOYING, FeedbackStatus.WAITING_VERIFY, FeedbackStatus.DONE};
        for (int i = 0; i < happyPath.length - 1; i++) {
            assertTrue(happyPath[i].canTransitionTo(happyPath[i + 1]),
                    happyPath[i] + " -> " + happyPath[i + 1] + " 应该允许");
        }
    }

    @Test
    void reopenGoesBackToTriageNotNewIssue() {
        assertTrue(FeedbackStatus.WAITING_VERIFY.canTransitionTo(FeedbackStatus.REOPENED));
        assertTrue(FeedbackStatus.REOPENED.canTransitionTo(FeedbackStatus.TRIAGING));
    }

    @Test
    void skipsAndIllegalTransitionsAreRejected() {
        assertFalse(FeedbackStatus.SUBMITTED.canTransitionTo(FeedbackStatus.DONE));
        assertFalse(FeedbackStatus.DONE.canTransitionTo(FeedbackStatus.TRIAGING));
        assertFalse(FeedbackStatus.PR_REVIEW.canTransitionTo(FeedbackStatus.WAITING_VERIFY));
        assertThrows(IllegalStateException.class, () -> {
            throw new IllegalStateException("非法状态迁移");
        });
    }

    @Test
    void displayStatusUsesExactlySevenVisibleStates() {
        var all = java.util.Arrays.stream(FeedbackStatus.values())
                .map(FeedbackStatus::displayStatus)
                .distinct()
                .toList();
        assertEquals(java.util.List.of("待处理", "AI 分析中", "开发处理中", "待发布", "待验证", "已完成", "需要补充")
                        .stream().sorted().toList(),
                all.stream().sorted().toList());
    }
}
