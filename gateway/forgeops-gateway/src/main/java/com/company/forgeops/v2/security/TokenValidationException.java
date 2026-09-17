package com.company.forgeops.v2.security;

/** Deliberately carries no token content for safe HTTP error handling. */
public class TokenValidationException extends RuntimeException {
    public TokenValidationException(String message) {
        super(message);
    }
}
