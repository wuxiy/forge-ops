package com.company.forgeops.v2.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * OPS-10 business metrics. Counters carry only enum-ish tag values — never subjects, prompts or secrets.
 * Exposed through /actuator/metrics alongside the health endpoints.
 */
@Service
public class ForgeOpsMetrics {

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final AtomicLong reconciliationCycles = new AtomicLong();
    private final AtomicLong stuckPlansRecovered = new AtomicLong();
    private final AtomicLong manualInterventions = new AtomicLong();

    public ForgeOpsMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("forgeops_reconciliation_cycles_total", reconciliationCycles);
        registry.gauge("forgeops_stuck_plans_recovered_total", stuckPlansRecovered);
        registry.gauge("forgeops_manual_interventions_total", manualInterventions);
    }

    public void gateDecision(String decision) {
        increment("forgeops_gate_decisions_total", "decision", decision);
    }

    public void plannerFallback(String reason) {
        increment("forgeops_planner_fallbacks_total", "reason", reason);
    }

    public void evidenceRejected(String reason) {
        increment("forgeops_evidence_rejected_total", "reason", reason);
    }

    public void outboxRetry() {
        increment("forgeops_outbox_retries_total", "outcome", "retry");
    }

    public void deferredEvent(String eventType) {
        increment("forgeops_deferred_events_total", "eventType", safeTag(eventType));
    }

    public void agentRunTerminal(String state) {
        increment("forgeops_agent_runs_terminal_total", "state", state);
    }

    public void selectionBreakerOpened() {
        increment("forgeops_selection_breaker_total", "event", "opened");
    }

    public void reconciliationCycle() {
        reconciliationCycles.incrementAndGet();
    }

    public void stuckPlanRecovered() {
        stuckPlansRecovered.incrementAndGet();
    }

    public void manualIntervention(String action) {
        manualInterventions.incrementAndGet();
        increment("forgeops_manual_interventions_detail_total", "action", safeTag(action));
    }

    private void increment(String name, String tagKey, String tagValue) {
        counters.computeIfAbsent(name + "/" + tagValue,
                key -> Counter.builder(name).tag(tagKey, safeTag(tagValue)).register(registry)).increment();
    }

    private static String safeTag(String value) {
        return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
