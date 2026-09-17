package com.company.forgeops.v2.verification.executor;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** ADR-0008: untrusted verification code runs in a one-shot isolated stack, never on the Gateway host. */
public interface VerificationExecutor {

    ExecutorResult execute(ExecutorTask task);

    record ExecutorTask(UUID planId, String category, int attempt, String image, List<String> command, String headSha,
            Path seedDir) {
    }

    record ExecutorResult(boolean passed, String outputDigest, String failureCategory, String message) {
    }

    class ExecutionRejected extends RuntimeException {
        public ExecutionRejected(String reason) {
            super(reason);
        }
    }
}
