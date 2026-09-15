package com.company.forgeops.v2.integration.github;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GitHubWebhookSignatureTest {

    @Test
    void acceptsGitHubsPublishedHmacSha256VectorAndRejectsModifiedInputs() {
        GitHubWebhookSignature signatures = new GitHubWebhookSignature();
        byte[] body = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";

        assertTrue(signatures.matches("It's a Secret to Everybody", header, body));
        assertFalse(signatures.matches("It's a Secret to Everybody", header, "Hello, world!".getBytes(StandardCharsets.UTF_8)));
        assertFalse(signatures.matches("It's a Secret to Everybody", "sha1=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17", body));
    }
}
