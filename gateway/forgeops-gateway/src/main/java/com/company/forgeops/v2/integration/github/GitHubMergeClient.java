package com.company.forgeops.v2.integration.github;

/** ADR-0005 machine-identity merge boundary. Distinct token and login from any human merge actor. */
public interface GitHubMergeClient {

    /**
     * Merges one pull request as the dedicated machine identity. The expected head SHA is passed so a racing
     * push can never be merged under stale gate evidence.
     */
    void mergePullRequest(String repository, long number, String expectedHeadSha);

    class MergeRejected extends RuntimeException {
        public MergeRejected(String reason) {
            super(reason);
        }
    }
}
