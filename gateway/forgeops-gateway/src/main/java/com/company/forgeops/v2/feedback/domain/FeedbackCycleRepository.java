package com.company.forgeops.v2.feedback.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FeedbackCycleRepository extends JpaRepository<FeedbackCycle, UUID> {

    Optional<FeedbackCycle> findByFeedbackIdAndCycleNo(UUID feedbackId, int cycleNo);

    @Query("select coalesce(max(c.cycleNo), 0) from FeedbackCycle c where c.feedbackId = :feedbackId")
    int findMaxCycleNo(UUID feedbackId);
}
