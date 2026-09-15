package com.company.forgeops.v2.integration.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GitHubWebhookNormalizerTest {

    private final GitHubWebhookNormalizer normalizer = new GitHubWebhookNormalizer(JsonMapper.shared());

    @Test
    void persistsOnlyPullRequestDeliveryFieldsAndDigest() throws Exception {
        byte[] body = """
                {"action":"closed","number":42,"repository":{"full_name":"example/pilot"},
                 "pull_request":{"base":{"ref":"main"},"head":{"ref":"forgeops/v2-42","sha":"abc123"},
                 "draft":false,"merged":true,"merged_by":{"login":"owner"},"body":"never persist this"}}
                """.getBytes(StandardCharsets.UTF_8);

        var normalized = normalizer.normalize("pull_request", body);
        var payload = JsonMapper.shared().readTree(normalized.payloadJson());

        assertEquals("PULL_REQUEST", normalized.eventType());
        assertEquals("example/pilot", normalized.repository());
        assertEquals("abc123", payload.get("headSha").asString());
        assertEquals("owner", payload.get("mergedBy").asString());
        assertFalse(normalized.payloadJson().contains("never persist this"));
        assertEquals(64, payload.get("payloadSha256").asString().length());
    }

    @Test
    void rejectsUnknownEventAndIncompleteProviderPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> normalizer.normalize("push", "{}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,
                () -> normalizer.normalize("pull_request", "{\"repository\":{\"full_name\":\"example/pilot\"}}"
                        .getBytes(StandardCharsets.UTF_8)));
    }
}
