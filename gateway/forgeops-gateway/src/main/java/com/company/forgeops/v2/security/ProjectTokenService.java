package com.company.forgeops.v2.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** HMAC-SHA-256 token codec for identities issued by the host backend, never by browser input. */
@Service
public class ProjectTokenService {

    private static final String VERSION = "v1";
    private final SecurityProperties properties;
    private final Clock clock;

    @Autowired
    public ProjectTokenService(SecurityProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ProjectTokenService(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(String subject, String projectId, Set<String> scopes) {
        if (blank(subject) || blank(projectId) || scopes == null || scopes.isEmpty()) {
            throw new IllegalArgumentException("subject, projectId and scopes are required");
        }
        if (containsReserved(subject) || containsReserved(projectId) || scopes.stream().anyMatch(this::containsReserved)) {
            throw new IllegalArgumentException("token field contains a reserved delimiter");
        }
        Instant expiry = clock.instant().plus(properties.getTokenTtl());
        String payload = String.join("|", VERSION, subject, projectId, String.join(",", new LinkedHashSet<>(scopes)),
                Long.toString(expiry.getEpochSecond()));
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    public ProjectToken verify(String encodedToken) {
        if (encodedToken == null || !encodedToken.contains(".")) {
            throw invalid();
        }
        String[] parts = encodedToken.split("\\.", -1);
        if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]).getBytes(StandardCharsets.US_ASCII),
                parts[1].getBytes(StandardCharsets.US_ASCII))) {
            throw invalid();
        }
        final String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            throw invalid();
        }
        String[] fields = payload.split("\\|", -1);
        if (fields.length != 5 || !VERSION.equals(fields[0]) || blank(fields[1]) || blank(fields[2]) || blank(fields[3])) {
            throw invalid();
        }
        final Instant expiresAt;
        try {
            expiresAt = Instant.ofEpochSecond(Long.parseLong(fields[4]));
        } catch (RuntimeException malformed) {
            throw invalid();
        }
        if (!expiresAt.isAfter(clock.instant())) {
            throw new TokenValidationException("token expired");
        }
        Set<String> scopes = Set.of(fields[3].split(","));
        if (scopes.contains("")) {
            throw invalid();
        }
        return new ProjectToken(fields[1], fields[2], scopes, expiresAt);
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getTokenSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception unavailable) {
            throw new IllegalStateException("cannot initialize token signature", unavailable);
        }
    }

    private TokenValidationException invalid() {
        return new TokenValidationException("invalid token");
    }

    private boolean containsReserved(String value) {
        return value == null || value.indexOf('|') >= 0 || value.indexOf(',') >= 0 || value.indexOf('.') >= 0;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
