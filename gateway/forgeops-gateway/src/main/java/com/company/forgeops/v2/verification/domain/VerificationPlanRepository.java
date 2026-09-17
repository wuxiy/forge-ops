package com.company.forgeops.v2.verification.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationPlanRepository extends JpaRepository<VerificationPlan, UUID> {

    Optional<VerificationPlan> findByDeliveryEvidenceIdAndStatusIn(UUID deliveryEvidenceId,
            List<VerificationPlanStatus> statuses);

    List<VerificationPlan> findByCycleIdOrderByCreatedAtDesc(UUID cycleId);

    Optional<VerificationPlan> findByAgentRunId(UUID agentRunId);

    /** Same tree re-push planning (ADR-0011): an existing plan for an unchanged head SHA is reused, not re-billed. */
    Optional<VerificationPlan> findFirstByCycleIdAndPrHeadShaAndStatusIn(UUID cycleId, String prHeadSha,
            List<VerificationPlanStatus> statuses);
}
