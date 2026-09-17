package com.company.forgeops.v2.verification.domain;

/** ADR-0007: edge origin. LLM edges may only expand the impact set or raise risk. */
public enum VerificationProvenance {
    STATIC,
    HUMAN,
    LLM,
    SYSTEM
}
