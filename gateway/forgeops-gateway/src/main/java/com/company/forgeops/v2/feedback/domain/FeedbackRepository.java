package com.company.forgeops.v2.feedback.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    Optional<Feedback> findByProjectIdAndDisplayNo(String projectId, long displayNo);
}
