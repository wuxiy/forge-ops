package com.company.forgeops.v2.feedback.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ProjectFeedbackCounterRepository extends JpaRepository<ProjectFeedbackCounter, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProjectFeedbackCounter> findByProjectId(@Param("projectId") String projectId);
}
