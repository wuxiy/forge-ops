package com.company.forgeops.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** forgeops_feedback_comment（§18.3）。 */
@Entity
@Table(name = "forgeops_feedback_comment")
public class FeedbackComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feedback_id", nullable = false)
    private Long feedbackId;

    @Column(name = "author_id", length = 64)
    private String authorId;

    @Column(name = "author_type", nullable = false, length = 16)
    private String authorType = "SYSTEM";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "JSONB")
    private String attachments;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }

    public Long getFeedbackId() { return feedbackId; }

    public void setFeedbackId(Long feedbackId) { this.feedbackId = feedbackId; }

    public String getAuthorId() { return authorId; }

    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getAuthorType() { return authorType; }

    public void setAuthorType(String authorType) { this.authorType = authorType; }

    public String getContent() { return content; }

    public void setContent(String content) { this.content = content; }

    public String getAttachments() { return attachments; }

    public void setAttachments(String attachments) { this.attachments = attachments; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
