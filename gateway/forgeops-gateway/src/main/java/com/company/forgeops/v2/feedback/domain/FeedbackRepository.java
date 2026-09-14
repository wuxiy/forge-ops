package com.company.forgeops.v2.feedback.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    Optional<Feedback> findByProjectIdAndDisplayNo(String projectId, long displayNo);

    Optional<Feedback> findByIdAndProjectId(UUID id, String projectId);

    List<Feedback> findByProjectIdAndReporterSubjectOrderByCreatedAtDesc(String projectId, String reporterSubject);
}
