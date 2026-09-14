package com.company.forgeops.v2.feedback.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContextSnapshotRepository extends JpaRepository<ContextSnapshot, UUID> {
    List<ContextSnapshot> findByCycleIdOrderByCreatedAtAsc(UUID cycleId);
}
