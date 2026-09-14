package com.company.forgeops.v2.integration.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    Optional<OutboxEvent> findByIdempotencyKey(String idempotencyKey);
}
