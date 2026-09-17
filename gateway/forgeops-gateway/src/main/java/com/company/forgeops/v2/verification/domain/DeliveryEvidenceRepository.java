package com.company.forgeops.v2.verification.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryEvidenceRepository extends JpaRepository<DeliveryEvidence, UUID> {
    Optional<DeliveryEvidence> findByAgentRunId(UUID agentRunId);

    List<DeliveryEvidence> findByState(DeliveryEvidenceState state);

    List<DeliveryEvidence> findByRepositoryAndPullRequestNoAndState(String repository, long pullRequestNo,
            DeliveryEvidenceState state);

    boolean existsByRepositoryAndPullRequestNo(String repository, long pullRequestNo);
}
