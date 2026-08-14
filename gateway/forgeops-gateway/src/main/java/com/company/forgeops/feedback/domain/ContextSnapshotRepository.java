package com.company.forgeops.feedback.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContextSnapshotRepository extends JpaRepository<ContextSnapshot, Long> {

    List<ContextSnapshot> findByFeedbackIdOrderByCreatedAtAsc(Long feedbackId);
}
