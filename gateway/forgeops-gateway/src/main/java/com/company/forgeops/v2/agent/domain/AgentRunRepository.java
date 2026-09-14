package com.company.forgeops.v2.agent.domain;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {
    Optional<AgentRun> findByIdempotencyKey(String idempotencyKey);

    List<AgentRun> findByState(AgentRunState state);

    List<AgentRun> findByFeedbackId(UUID feedbackId);
}
