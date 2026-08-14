package com.company.forgeops.feedback.api.dto;

import com.company.forgeops.feedback.domain.Feedback;
import com.company.forgeops.feedback.domain.FeedbackComment;
import java.time.OffsetDateTime;
import java.util.List;

public record FeedbackDetailDto(
        String id,
        String projectId,
        String type,
        String title,
        String description,
        String expectedBehavior,
        String status,
        String displayStatus,
        String reporter,
        String environment,
        String pageUrl,
        String frontendVersion,
        String backendVersion,
        String multicaIssueUrl,
        String prUrl,
        String deploymentVersion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<CommentDto> comments) {

    public record CommentDto(
            Long id,
            String authorType,
            String author,
            String content,
            OffsetDateTime createdAt) {
    }

    public static FeedbackDetailDto from(Feedback f, List<FeedbackComment> comments) {
        return new FeedbackDetailDto(
                f.identifier(),
                f.getProjectId(),
                f.getType(),
                f.getTitle(),
                f.getDescription(),
                f.getExpectedBehavior(),
                f.getStatus().name(),
                f.getStatus().displayStatus(),
                f.getReporterName(),
                f.getEnvironment(),
                f.getPageUrl(),
                f.getFrontendVersion(),
                f.getBackendVersion(),
                f.getMulticaIssueUrl(),
                f.getPrUrl(),
                f.getDeploymentVersion(),
                f.getCreatedAt(),
                f.getUpdatedAt(),
                comments.stream()
                        .map(c -> new CommentDto(
                                c.getId(),
                                c.getAuthorType(),
                                c.getAuthorId() == null ? c.getAuthorType() : c.getAuthorId(),
                                c.getContent(),
                                c.getCreatedAt()))
                        .toList());
    }
}
