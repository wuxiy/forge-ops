package com.company.forgeops.v2.integration.inbox;

import com.company.forgeops.v2.audit.AuditTrail;
import com.company.forgeops.v2.integration.domain.IntegrationEvent;
import com.company.forgeops.v2.integration.domain.IntegrationEventRepository;
import com.company.forgeops.v2.integration.domain.IntegrationEventState;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import com.company.forgeops.v2.observability.ForgeOpsMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Inbox persistence precedes every workflow application; duplicate events return their original fact. */
@Service
public class IntegrationInbox {

    private final IntegrationEventRepository events;
    private final IntegrationApplier applier;
    private final AuditTrail auditTrail;
    private final TransactionTemplate transactions;
    private final ForgeOpsMetrics metrics;

    public IntegrationInbox(IntegrationEventRepository events, IntegrationApplier applier, AuditTrail auditTrail,
            TransactionTemplate transactions, ForgeOpsMetrics metrics) {
        this.events = events;
        this.applier = applier;
        this.auditTrail = auditTrail;
        this.transactions = transactions;
        this.metrics = metrics;
    }

    public InboxReceipt accept(InboundEvent inbound) {
        validate(inbound);
        IntegrationEvent existing = events.findBySourceAndExternalEventId(inbound.source(), inbound.externalEventId())
                .orElse(null);
        if (existing != null) {
            return new InboxReceipt(existing.getId(), existing.getState(), true);
        }
        try {
            return transactions.execute(status -> persistAndApply(inbound));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            IntegrationEvent duplicate = events.findBySourceAndExternalEventId(inbound.source(), inbound.externalEventId())
                    .orElseThrow(() -> concurrentDuplicate);
            return duplicate.getState() == IntegrationEventState.RECEIVED
                    ? apply(duplicate.getId(), true)
                    : new InboxReceipt(duplicate.getId(), duplicate.getState(), true);
        }
    }

    public int reconcileDeferred() {
        List<UUID> deferred = transactions.execute(status -> events.findByStateIn(
                EnumSet.of(IntegrationEventState.RECEIVED, IntegrationEventState.DEFERRED)).stream()
                .map(IntegrationEvent::getId).toList());
        if (deferred == null) {
            return 0;
        }
        deferred.forEach(id -> apply(id, false));
        return deferred.size();
    }

    public InboxReceipt apply(UUID eventId, boolean duplicate) {
        return transactions.execute(status -> {
            IntegrationEvent event = events.findById(eventId).orElseThrow();
            if (event.getState() == IntegrationEventState.APPLIED || event.getState() == IntegrationEventState.REJECTED) {
                return new InboxReceipt(eventId, event.getState(), duplicate);
            }
            return applyInCurrentTransaction(event, duplicate);
        });
    }

    private InboxReceipt persistAndApply(InboundEvent inbound) {
        IntegrationEvent event = IntegrationEvent.receive(inbound.source(), inbound.externalEventId(), inbound.eventType(),
                inbound.projectId(), inbound.feedbackId(), inbound.cycleId(), inbound.agentRunId(), inbound.payloadJson());
        event = events.save(event);
        auditTrail.record(inbound.feedbackId(), inbound.cycleId(), inbound.agentRunId(), "integration:" + inbound.source(),
                "INBOX_RECEIVED", "SUCCESS", inbound.traceId(),
                "{\"eventId\":\"" + event.getId() + "\",\"eventType\":\"" + inbound.eventType() + "\"}");
        return applyInCurrentTransaction(event, false);
    }

    private InboxReceipt applyInCurrentTransaction(IntegrationEvent event, boolean duplicate) {
        IntegrationApplier.ApplyResult result = applier.apply(event);
        switch (result.disposition()) {
            case APPLIED -> event.applied();
            case DEFERRED -> {
                event.deferred(result.detail());
                metrics.deferredEvent(event.getEventType());
            }
            case REJECTED -> event.rejected(result.detail());
        }
        auditTrail.record(event.getFeedbackId(), event.getCycleId(), event.getAgentRunId(), "integration:" + event.getSource(),
                "INBOX_APPLIED", result.disposition().name(), null,
                "{\"eventId\":\"" + event.getId() + "\",\"eventType\":\"" + event.getEventType() + "\"}");
        return new InboxReceipt(event.getId(), event.getState(), duplicate);
    }

    private static void validate(InboundEvent event) {
        if (event == null || blank(event.source()) || blank(event.externalEventId()) || blank(event.eventType())
                || blank(event.projectId()) || blank(event.payloadJson())) {
            throw new IllegalArgumentException("source, externalEventId, eventType, projectId and payloadJson are required");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
