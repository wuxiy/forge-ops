package com.company.forgeops.v2.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectTokenServiceTest {

    private final Instant now = Instant.parse("2026-09-14T00:00:00Z");

    @Test
    void acceptsOnlyIntactUnexpiredProjectScopedTokens() {
        ProjectTokenService service = service(Duration.ofMinutes(5));
        String encoded = service.issue("user-a", "project-a", Set.of("feedback:read", "feedback:write"));

        ProjectToken token = service.verify(encoded);

        assertEquals("user-a", token.subject());
        assertEquals("project-a", token.projectId());
        assertTrue(token.allows("feedback:write"));
        assertThrows(TokenValidationException.class, () -> service.verify(encoded + "x"));
    }

    @Test
    void rejectsExpiredToken() {
        ProjectTokenService issuer = service(Duration.ofSeconds(-1));
        String expired = issuer.issue("user-a", "project-a", Set.of("feedback:read"));

        assertThrows(TokenValidationException.class, () -> issuer.verify(expired));
    }

    private ProjectTokenService service(Duration ttl) {
        SecurityProperties properties = new SecurityProperties();
        properties.setTokenSecret("test-secret-for-token-code-only");
        properties.setTokenTtl(ttl);
        return new ProjectTokenService(properties, Clock.fixed(now, ZoneOffset.UTC));
    }
}
