package com.company.forgeops.v2.verification;

import com.company.forgeops.v2.integration.github.GitHubPullRequestClient.ObservedPullRequest;
import com.company.forgeops.v2.verification.domain.DeliveryEvidence;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Exact, code-owned comparison between an Agent claim and GitHub's independently fetched pull request. */
@Component
public class GitHubPullRequestEvidenceMatcher {

    private final ObjectMapper json;

    public GitHubPullRequestEvidenceMatcher(ObjectMapper json) {
        this.json = json;
    }

    public MatchResult match(DeliveryEvidence expected, ObservedPullRequest observed) {
        List<String> mismatches = new ArrayList<>();
        equal(mismatches, "repository", expected.getRepository(), observed.repository());
        if (expected.getPullRequestNo() != observed.number()) mismatches.add("pullRequestNo");
        equal(mismatches, "htmlUrl", expected.getExpectedPrUrl(), observed.htmlUrl());
        equal(mismatches, "state", "open", observed.state());
        if (!observed.draft()) mismatches.add("draft");
        if (observed.merged()) mismatches.add("merged");
        equal(mismatches, "baseBranch", expected.getExpectedBaseBranch(), observed.baseBranch());
        equal(mismatches, "headBranch", expected.getExpectedHeadBranch(), observed.headBranch());
        equal(mismatches, "headSha", expected.getExpectedHeadSha(), observed.headSha());
        return new MatchResult(mismatches.isEmpty(), List.copyOf(mismatches), observedJson(observed));
    }

    private String observedJson(ObservedPullRequest observed) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("repository", observed.repository());
        fact.put("pullRequestNo", observed.number());
        fact.put("htmlUrl", observed.htmlUrl());
        fact.put("state", observed.state());
        fact.put("draft", observed.draft());
        fact.put("merged", observed.merged());
        fact.put("baseBranch", observed.baseBranch());
        fact.put("headBranch", observed.headBranch());
        fact.put("headSha", observed.headSha());
        try {
            return json.writeValueAsString(fact);
        } catch (JacksonException failure) {
            throw new IllegalStateException("Cannot persist observed GitHub PR fact", failure);
        }
    }

    private static void equal(List<String> mismatches, String field, String expected, String actual) {
        if (!java.util.Objects.equals(expected, actual)) mismatches.add(field);
    }

    public record MatchResult(boolean matches, List<String> mismatches, String observedJson) {
    }
}
