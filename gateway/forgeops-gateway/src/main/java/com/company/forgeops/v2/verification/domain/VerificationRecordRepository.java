package com.company.forgeops.v2.verification.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationRecordRepository extends JpaRepository<VerificationRecord, UUID> {
}
