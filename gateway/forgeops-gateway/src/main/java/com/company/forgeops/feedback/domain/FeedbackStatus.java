package com.company.forgeops.feedback.domain;

import java.util.Map;
import java.util.Set;

/**
 * 反馈内部技术状态机（架构文档 §6.1）。
 * 内部状态由 Gateway 维护；对测试/产品只暴露 §6.2 的用户可见状态。
 */
public enum FeedbackStatus {

    SUBMITTED,
    CONTEXT_BUILDING,
    TRIAGING,
    CODING,
    PR_REVIEW,
    BUILDING,
    DEPLOYING,
    WAITING_VERIFY,
    DONE,
    NEED_INFO,
    AGENT_FAILED,
    BUILD_FAILED,
    CHANGES_REQUESTED,
    REOPENED;

    private static final Map<FeedbackStatus, Set<FeedbackStatus>> TRANSITIONS = Map.ofEntries(
            Map.entry(SUBMITTED, Set.of(CONTEXT_BUILDING, TRIAGING)),
            Map.entry(CONTEXT_BUILDING, Set.of(TRIAGING, NEED_INFO)),
            Map.entry(TRIAGING, Set.of(CODING, NEED_INFO)),
            Map.entry(NEED_INFO, Set.of(TRIAGING, CODING, CONTEXT_BUILDING)),
            Map.entry(CODING, Set.of(PR_REVIEW, AGENT_FAILED, NEED_INFO)),
            Map.entry(AGENT_FAILED, Set.of(CODING, NEED_INFO)),
            Map.entry(PR_REVIEW, Set.of(BUILDING, CHANGES_REQUESTED, CODING)),
            Map.entry(CHANGES_REQUESTED, Set.of(CODING, PR_REVIEW)),
            Map.entry(BUILDING, Set.of(DEPLOYING, BUILD_FAILED)),
            Map.entry(BUILD_FAILED, Set.of(BUILDING)),
            Map.entry(DEPLOYING, Set.of(WAITING_VERIFY, BUILD_FAILED)),
            Map.entry(WAITING_VERIFY, Set.of(DONE, REOPENED)),
            Map.entry(REOPENED, Set.of(TRIAGING, CODING, CONTEXT_BUILDING)),
            Map.entry(DONE, Set.of()));

    public boolean canTransitionTo(FeedbackStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** 用户可见状态（§6.2 七态）。 */
    public String displayStatus() {
        return switch (this) {
            case SUBMITTED, CONTEXT_BUILDING -> "待处理";
            case TRIAGING -> "AI 分析中";
            case CODING, AGENT_FAILED, CHANGES_REQUESTED, REOPENED -> "开发处理中";
            case PR_REVIEW, BUILDING, DEPLOYING, BUILD_FAILED -> "待发布";
            case WAITING_VERIFY -> "待验证";
            case DONE -> "已完成";
            case NEED_INFO -> "需要补充";
        };
    }

    /** Agent 正在或即将工作的活跃状态（poller 关心的集合）。 */
    public static Set<FeedbackStatus> agentActive() {
        return Set.of(TRIAGING, CODING, NEED_INFO, AGENT_FAILED);
    }
}
