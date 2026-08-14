package com.company.forgeops.feedback.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackCommentRepository extends JpaRepository<FeedbackComment, Long> {

    List<FeedbackComment> findByFeedbackIdOrderByCreatedAtAsc(Long feedbackId);
}
