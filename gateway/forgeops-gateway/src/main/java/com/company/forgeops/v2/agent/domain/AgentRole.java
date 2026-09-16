package com.company.forgeops.v2.agent.domain;

public enum AgentRole {
    TRIAGE,
    CODING,
    VERIFICATION,
    FAILURE_ANALYSIS;

    /** Verification-family roles run read-only: isolated task directory, no repository worktree, no Git write credentials. */
    public boolean isVerificationFamily() {
        return this == VERIFICATION || this == FAILURE_ANALYSIS;
    }
}
