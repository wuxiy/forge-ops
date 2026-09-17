package com.company.forgeops.v2.agent.execution;

/** ForgeOps-owned execution seam. Callers never depend on Paseo types or timelines. */
public interface AgentExecution {

    RuntimeRunSnapshot submit(RuntimeRunRequest request);

    RuntimeRunSnapshot inspect(String idempotencyKey);

    RuntimeRunSnapshot cancel(String idempotencyKey);
}
