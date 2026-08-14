package com.company.forgeops.feedback.api.dto;

import com.company.forgeops.feedback.domain.Feedback;
import com.company.forgeops.feedback.domain.FeedbackComment;
import java.time.OffsetDateTime;
import java.util.List;

/** SDK 消费的视图 DTO（用户可见状态 = §6.2 七态）。 */
public record FeedbackListItemDto(
        String id,
        String projectId,
        String type,
        String title,
        String status,
        String displayStatus,
        String reporter,
        String environment,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String deploymentVersion,
        String prUrl) {

    public static FeedbackListItemDto from(Feedback f) {
        return new FeedbackListItemDto(
                f.identifier(),
                f.getProjectId(),
                f.getType(),
                f.getTitle(),
                f.getStatus().name(),
                f.getStatus().displayStatus(),
                f.getReporterName(),
                f.getEnvironment(),
                f.getCreatedAt(),
                f.getUpdatedAt(),
                f.getDeploymentVersion(),
                f.getPrUrl());
    }
}
