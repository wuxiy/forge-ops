package com.company.forgeops.v2.integration.github;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Verifies GitHub's X-Hub-Signature-256 against the unmodified request bytes. */
@Component
public class GitHubWebhookSignature {

    private static final String PREFIX = "sha256=";

    public boolean matches(String secret, String header, byte[] body) {
        if (secret == null || secret.isBlank() || header == null || !header.startsWith(PREFIX) || body == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(body);
            byte[] supplied = decodeHex(header.substring(PREFIX.length()));
            return supplied != null && MessageDigest.isEqual(expected, supplied);
        } catch (Exception unavailable) {
            return false;
        }
    }

    private static byte[] decodeHex(String value) {
        if (value.length() != 64 || !value.matches("[0-9a-fA-F]{64}")) {
            return null;
        }
        byte[] result = new byte[32];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
