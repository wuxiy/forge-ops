package com.company.forgeops.v2.verification.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationRunRepository extends JpaRepository<VerificationRun, UUID> {

    List<VerificationRun> findByPlanIdOrderByCategoryAscAttemptAsc(UUID planId);

    List<VerificationRun> findByPlanIdAndCategoryOrderByAttemptDesc(UUID planId, String category);
}
