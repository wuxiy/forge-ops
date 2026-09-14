package com.company.forgeops.v2.security;

/** Request-local identity set only by the v2 bearer-token filter. */
public final class CallerIdentity {

    private static final ThreadLocal<ProjectToken> CURRENT = new ThreadLocal<>();

    private CallerIdentity() {
    }

    public static void set(ProjectToken token) { CURRENT.set(token); }

    public static ProjectToken require() {
        ProjectToken token = CURRENT.get();
        if (token == null) {
            throw new TokenValidationException("missing authenticated identity");
        }
        return token;
    }

    public static void clear() { CURRENT.remove(); }
}
