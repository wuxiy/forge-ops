package com.company.forgeops.v2.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.forgeops.v2.integration.github.GitHubPullRequestClient.ObservedPullRequest;
import com.company.forgeops.v2.verification.domain.DeliveryEvidence;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GitHubPullRequestEvidenceMatcherTest {

    private final GitHubPullRequestEvidenceMatcher matcher = new GitHubPullRequestEvidenceMatcher(JsonMapper.shared());

    @Test
    void exactOpenDraftProviderFactIsAccepted() {
        var result = matcher.match(expected(), observed("forgeops/v2-42", "abc123", true, "open"));

        assertTrue(result.matches());
        assertTrue(result.mismatches().isEmpty());
        assertTrue(result.observedJson().contains("\"headSha\":\"abc123\""));
    }

    @Test
    void wrongCommitOrNonDraftProviderFactIsRejectedWithNamedMismatches() {
        var result = matcher.match(expected(), observed("other-branch", "wrong-sha", false, "closed"));

        assertFalse(result.matches());
        assertEquals(java.util.List.of("state", "draft", "headBranch", "headSha"), result.mismatches());
    }

    private static DeliveryEvidence expected() {
        return DeliveryEvidence.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "example/pilot", 42,
                "main", "forgeops/v2-42", "abc123", "https://github.com/example/pilot/pull/42");
    }

    private static ObservedPullRequest observed(String branch, String sha, boolean draft, String state) {
        return new ObservedPullRequest("example/pilot", 42, "https://github.com/example/pilot/pull/42", state, draft,
                branch, sha, "main", false, null);
    }
}
