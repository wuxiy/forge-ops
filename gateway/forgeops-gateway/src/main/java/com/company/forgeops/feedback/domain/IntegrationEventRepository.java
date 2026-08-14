package com.company.forgeops.feedback.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationEventRepository extends JpaRepository<IntegrationEvent, Long> {

    Optional<IntegrationEvent> findBySourceAndExternalEventId(String source, String externalEventId);
}
