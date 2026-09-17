package com.company.forgeops.v2.security;

import java.time.Instant;
import java.util.Set;

/** Signed browser identity, scoped to one project and a minimal set of actions. */
public record ProjectToken(String subject, String projectId, Set<String> scopes, Instant expiresAt) {

    public boolean allows(String scope) {
        return scopes.contains(scope);
    }
}
