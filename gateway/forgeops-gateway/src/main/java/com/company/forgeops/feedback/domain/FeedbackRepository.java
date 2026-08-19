package com.company.forgeops.feedback.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByReporterNameAndProjectIdOrderByCreatedAtDesc(String reporterName, String projectId);

    Optional<Feedback> findByMulticaIssueId(String multicaIssueId);

    Optional<Feedback> findByFeedbackPrefixAndDisplayNo(String feedbackPrefix, Long displayNo);

    Optional<Feedback> findByFeedbackPrefixIsNullAndDisplayNo(Long displayNo);

    List<Feedback> findByStatusIn(List<FeedbackStatus> statuses);

    List<Feedback> findByReporterNameAndProjectId(String reporterName, String projectId, Pageable pageable);
}
