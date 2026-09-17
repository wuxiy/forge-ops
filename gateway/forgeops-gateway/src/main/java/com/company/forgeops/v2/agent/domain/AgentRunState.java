package com.company.forgeops.v2.agent.domain;

public enum AgentRunState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    INVALID_OUTPUT,
    CANCELLED,
    TIMED_OUT
}
