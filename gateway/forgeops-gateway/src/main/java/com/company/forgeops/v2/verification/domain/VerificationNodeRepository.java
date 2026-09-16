package com.company.forgeops.v2.verification.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationNodeRepository extends JpaRepository<VerificationNode, UUID> {

    List<VerificationNode> findByProjectIdAndBaselineCommit(String projectId, String baselineCommit);

    Optional<VerificationNode> findByProjectIdAndTypeAndPathAndBaselineCommit(String projectId, VerificationNode.Type type,
            String path, String baselineCommit);
}
