package com.company.forgeops.v2.verification.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationEvidenceRepository extends JpaRepository<VerificationEvidence, UUID> {

    List<VerificationEvidence> findByPlanIdOrderByCollectedAtAsc(UUID planId);

    List<VerificationEvidence> findByExpiresAtBeforeAndPurgedAtIsNull(java.time.OffsetDateTime now);

    boolean existsByPlanIdAndPayloadDigest(UUID planId, String payloadDigest);
}
