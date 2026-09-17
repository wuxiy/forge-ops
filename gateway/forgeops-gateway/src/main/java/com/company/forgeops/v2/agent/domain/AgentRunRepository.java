package com.company.forgeops.v2.agent.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {
    Optional<AgentRun> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from AgentRun run where run.id = :id")
    Optional<AgentRun> lockById(@Param("id") UUID id);

    List<AgentRun> findByState(AgentRunState state);

    List<AgentRun> findByFeedbackId(UUID feedbackId);

    @Query("select coalesce(max(run.attempt), 0) from AgentRun run where run.cycleId = :cycleId and run.role = :role")
    int findMaxAttemptByCycleIdAndRole(@Param("cycleId") UUID cycleId, @Param("role") AgentRole role);
}
