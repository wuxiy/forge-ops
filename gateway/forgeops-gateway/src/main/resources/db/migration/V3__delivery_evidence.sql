CREATE TABLE delivery_evidence (
    id UUID PRIMARY KEY,
    feedback_id UUID NOT NULL REFERENCES feedback(id),
    cycle_id UUID NOT NULL REFERENCES feedback_cycle(id),
    agent_run_id UUID NOT NULL REFERENCES agent_run(id),
    provider VARCHAR(16) NOT NULL,
    repository VARCHAR(256) NOT NULL,
    pull_request_no BIGINT NOT NULL,
    expected_base_branch VARCHAR(256) NOT NULL,
    expected_head_branch VARCHAR(256) NOT NULL,
    expected_head_sha VARCHAR(128) NOT NULL,
    expected_pr_url TEXT NOT NULL,
    state VARCHAR(16) NOT NULL,
    query_attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    observed_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_delivery_evidence_agent_run UNIQUE (agent_run_id)
);

CREATE INDEX idx_delivery_evidence_reconcile ON delivery_evidence (state, updated_at);
