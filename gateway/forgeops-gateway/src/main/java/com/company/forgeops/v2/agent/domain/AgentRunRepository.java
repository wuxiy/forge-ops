package com.company.forgeops.v2.agent.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {
    Optional<AgentRun> findByIdempotencyKey(String idempotencyKey);
}
