package com.company.forgeops.v2.integration.github;

/** The evidence fact stays pending when GitHub cannot be queried reliably. */
public class GitHubProviderUnavailableException extends RuntimeException {
    public GitHubProviderUnavailableException(String message) {
        super(message);
    }

    public GitHubProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
