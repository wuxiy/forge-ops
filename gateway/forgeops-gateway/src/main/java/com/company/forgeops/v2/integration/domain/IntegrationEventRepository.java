package com.company.forgeops.v2.integration.domain;

import java.util.Optional;
import java.util.Collection;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationEventRepository extends JpaRepository<IntegrationEvent, UUID> {
    Optional<IntegrationEvent> findBySourceAndExternalEventId(String source, String externalEventId);

    List<IntegrationEvent> findByState(IntegrationEventState state);

    List<IntegrationEvent> findByStateIn(Collection<IntegrationEventState> states);
}
