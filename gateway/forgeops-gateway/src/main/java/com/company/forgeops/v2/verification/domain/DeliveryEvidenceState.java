package com.company.forgeops.v2.verification.domain;

/** A delivery declaration is pending until the configured provider independently confirms every bound fact. */
public enum DeliveryEvidenceState {
    PENDING,
    VERIFIED,
    REJECTED
}
