package com.company.forgeops.v2.agent.execution;

/** A retryable private Runtime boundary failure; the Outbox owns retry policy. */
public class RuntimeUnavailableException extends RuntimeException {

    public RuntimeUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public RuntimeUnavailableException(String message) {
        super(message);
    }
}
