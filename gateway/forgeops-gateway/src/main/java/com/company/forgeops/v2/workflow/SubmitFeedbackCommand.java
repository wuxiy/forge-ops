package com.company.forgeops.v2.workflow;

/** Context must already be redacted by ContextPreparation before entering this command. */
public record SubmitFeedbackCommand(
        String projectId,
        String reporterSubject,
        String title,
        String description,
        String redactedContextJson,
        String contextSha256,
        int redactionCount,
        String traceId) {
}
