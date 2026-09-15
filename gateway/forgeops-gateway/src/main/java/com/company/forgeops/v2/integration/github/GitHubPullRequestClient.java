package com.company.forgeops.v2.integration.github;

/** Read-only provider boundary. The Agent's PR URL is never treated as a provider response. */
public interface GitHubPullRequestClient {
    ObservedPullRequest getPullRequest(String repository, long number);

    record ObservedPullRequest(String repository, long number, String htmlUrl, String state, boolean draft,
            String headBranch, String headSha, String baseBranch, boolean merged, String mergedBy) {
    }
}
