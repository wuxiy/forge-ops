package com.company.forgeops.v2.integration.domain;

import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    Optional<OutboxEvent> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OutboxEvent o where o.state in :states and o.nextAttemptAt <= :now order by o.nextAttemptAt asc")
    List<OutboxEvent> lockDue(Collection<OutboxEventState> states, OffsetDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OutboxEvent o where o.id = :eventId")
    Optional<OutboxEvent> lockById(UUID eventId);

    List<OutboxEvent> findByStateAndUpdatedAtBefore(OutboxEventState state, OffsetDateTime cutoff);
}
