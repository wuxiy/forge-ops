package com.company.forgeops.v2.audit;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditTrail {

    private final AuditLogRepository repository;

    public AuditTrail(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(UUID feedbackId, UUID cycleId, UUID agentRunId, String actor, String action, String outcome,
            String traceId, String redactedDetailJson) {
        repository.save(AuditLog.record(feedbackId, cycleId, agentRunId, actor, action, outcome, traceId,
                redactedDetailJson));
    }
}
