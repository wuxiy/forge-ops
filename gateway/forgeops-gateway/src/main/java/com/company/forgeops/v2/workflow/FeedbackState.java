package com.company.forgeops.v2.workflow;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/** The complete v2 workflow state machine. State changes are owned by FeedbackWorkflow only. */
public enum FeedbackState {
    RECEIVED,
    CONTEXT_READY,
    TRIAGE_QUEUED,
    TRIAGE_RUNNING,
    NEEDS_INPUT,
    NO_CODE_REQUIRED,
    TRIAGE_FAILED,
    CODE_QUEUED,
    CODE_RUNNING,
    EXECUTION_FAILED,
    PR_READY,
    VERIFY_RUNNING,
    VERIFY_FAILED,
    GATE_PASS,
    BUILD_RUNNING,
    BUILD_FAILED,
    DEPLOY_RUNNING,
    DEPLOY_FAILED,
    WAITING_VERIFY,
    DONE,
    REOPENED;

    private static final EnumMap<FeedbackState, Set<FeedbackState>> TRANSITIONS = transitions();

    private static EnumMap<FeedbackState, Set<FeedbackState>> transitions() {
        var result = new EnumMap<FeedbackState, Set<FeedbackState>>(FeedbackState.class);
        result.put(RECEIVED, EnumSet.of(CONTEXT_READY));
        result.put(CONTEXT_READY, EnumSet.of(TRIAGE_QUEUED));
        result.put(TRIAGE_QUEUED, EnumSet.of(TRIAGE_RUNNING, TRIAGE_FAILED));
        result.put(TRIAGE_RUNNING, EnumSet.of(NEEDS_INPUT, NO_CODE_REQUIRED, TRIAGE_FAILED, CODE_QUEUED));
        result.put(NEEDS_INPUT, EnumSet.of(CONTEXT_READY, TRIAGE_QUEUED));
        result.put(NO_CODE_REQUIRED, EnumSet.of(WAITING_VERIFY, REOPENED));
        result.put(TRIAGE_FAILED, EnumSet.of(TRIAGE_QUEUED));
        result.put(CODE_QUEUED, EnumSet.of(CODE_RUNNING, EXECUTION_FAILED));
        result.put(CODE_RUNNING, EnumSet.of(EXECUTION_FAILED, PR_READY));
        result.put(EXECUTION_FAILED, EnumSet.of(CODE_QUEUED));
        result.put(PR_READY, EnumSet.of(VERIFY_RUNNING));
        result.put(VERIFY_RUNNING, EnumSet.of(VERIFY_FAILED, GATE_PASS));
        result.put(VERIFY_FAILED, EnumSet.of(VERIFY_RUNNING, CODE_QUEUED));
        result.put(GATE_PASS, EnumSet.of(BUILD_RUNNING));
        result.put(BUILD_RUNNING, EnumSet.of(BUILD_FAILED, DEPLOY_RUNNING));
        result.put(BUILD_FAILED, EnumSet.of(BUILD_RUNNING));
        result.put(DEPLOY_RUNNING, EnumSet.of(DEPLOY_FAILED, WAITING_VERIFY));
        result.put(DEPLOY_FAILED, EnumSet.of(DEPLOY_RUNNING));
        result.put(WAITING_VERIFY, EnumSet.of(DONE, REOPENED));
        result.put(DONE, EnumSet.of(REOPENED));
        result.put(REOPENED, EnumSet.of(CONTEXT_READY));
        return result;
    }

    public boolean canTransitionTo(FeedbackState target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public String userVisibleStatus() {
        return switch (this) {
            case RECEIVED, CONTEXT_READY, TRIAGE_QUEUED -> "OPEN";
            case TRIAGE_RUNNING, CODE_QUEUED, CODE_RUNNING, PR_READY, VERIFY_RUNNING, GATE_PASS, BUILD_RUNNING,
                    DEPLOY_RUNNING -> "IN_PROGRESS";
            case NEEDS_INPUT -> "NEEDS_INPUT";
            case NO_CODE_REQUIRED, WAITING_VERIFY -> "WAITING_VERIFY";
            case DONE -> "DONE";
            case TRIAGE_FAILED, EXECUTION_FAILED, VERIFY_FAILED, BUILD_FAILED, DEPLOY_FAILED -> "FAILED";
            case REOPENED -> "OPEN";
        };
    }
}
