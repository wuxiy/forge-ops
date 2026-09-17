package com.company.forgeops.v2.verification.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationEdgeRepository extends JpaRepository<VerificationEdge, UUID> {

    List<VerificationEdge> findByProjectIdAndFromNodeIdIn(String projectId, List<UUID> fromNodeIds);

    List<VerificationEdge> findByProjectId(String projectId);
}
