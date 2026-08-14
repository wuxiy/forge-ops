package com.company.forgeops.feedback.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * SDK -> Gateway 提交载荷（schemas/feedback.schema.json 的 Java 投影）。
 * DTO 即白名单：未知字段在反序列化时被丢弃，敏感头/体根本不存在于该结构。
 */
public record FeedbackSubmissionDto(
        @Pattern(regexp = "1\\.0") String schemaVersion,
        @NotBlank @Size(max = 64) String projectId,
        @Pattern(regexp = "BUG|OPTIMIZATION|REQUIREMENT") String type,
        @Size(max = 120) String title,
        @NotBlank @Size(max = 8000) String description,
        @Size(max = 4000) String expectedBehavior,
        @Size(max = 4000) String steps,
        @Size(max = 4000) String note,
        @jakarta.validation.Valid Reporter reporter,
        @Pattern(regexp = "test|uat|staging") String environment,
        Page page,
        Client client,
        VersionInfo frontend,
        VersionInfo backend,
        List<RequestSummary> requests,
        List<String> consoleErrors,
        @Size(max = 5_242_880) @Pattern(regexp = "^data:image/png;base64,.*") String screenshot,
        String occurredAt) {

    public record Reporter(String id, @NotBlank @Size(max = 64) String name) {
    }

    public record Page(String url, String route, String title) {
    }

    public record Client(String userAgent, String platform, String language, String screen, String viewport) {
    }

    public record VersionInfo(String version, String commit) {
    }

    public record RequestSummary(
            String method,
            String url,
            Integer status,
            Integer durationMs,
            String requestId,
            String traceId,
            String time) {
    }

    @Override
    public String toString() {
        return "FeedbackSubmissionDto[projectId=" + projectId + ", type=" + type
                + ", reporter=" + (reporter == null ? null : reporter.name()) + "]";
    }
}
