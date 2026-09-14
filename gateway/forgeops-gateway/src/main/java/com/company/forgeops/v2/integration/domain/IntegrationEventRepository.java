package com.company.forgeops.v2.integration.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationEventRepository extends JpaRepository<IntegrationEvent, UUID> {
    Optional<IntegrationEvent> findBySourceAndExternalEventId(String source, String externalEventId);
}
