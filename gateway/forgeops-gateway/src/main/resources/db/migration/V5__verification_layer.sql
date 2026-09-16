-- Verification layer (ADR-0002/0005/0006/0007/0008/0009/0010/0011/0012).
-- Every verification entity is foreign-key anchored to the Cycle that produced the PR (ADR-0012).

CREATE TABLE verification_plan (
    id UUID PRIMARY KEY,
    feedback_id UUID NOT NULL REFERENCES feedback(id),
    cycle_id UUID NOT NULL REFERENCES feedback_cycle(id),
    agent_run_id UUID REFERENCES agent_run(id),
    delivery_evidence_id UUID NOT NULL REFERENCES delivery_evidence(id),
    pr_head_sha VARCHAR(128) NOT NULL,
    pr_url TEXT NOT NULL,
    origin VARCHAR(16) NOT NULL,
    fallback_reason VARCHAR(64),
    selection_mode VARCHAR(16) NOT NULL,
    required_categories JSONB NOT NULL,
    selected_categories JSONB NOT NULL,
    impact_files JSONB NOT NULL,
    risk_level VARCHAR(16),
    graph_baseline_commit VARCHAR(128),
    status VARCHAR(24) NOT NULL,
    gate_decision VARCHAR(8),
    gate_reason JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_verification_plan_origin CHECK (origin IN ('PLANNER', 'FALLBACK')),
    CONSTRAINT ck_verification_plan_mode CHECK (selection_mode IN ('ADDITIVE', 'SUBTRACTIVE'))
);

-- Only one open plan per cycle at a time; terminal plans free the slot for a re-push.
CREATE UNIQUE INDEX uq_verification_plan_open ON verification_plan (cycle_id)
    WHERE status IN ('PLANNED', 'RUNNING');

CREATE TABLE verification_run (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES verification_plan(id),
    category VARCHAR(64) NOT NULL,
    source VARCHAR(16) NOT NULL,
    head_sha VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt INTEGER NOT NULL,
    external_ref VARCHAR(256),
    failure_category VARCHAR(64),
    failure_message TEXT,
    seed_digest VARCHAR(64),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_verification_run_source CHECK (source IN ('PR_CHECK', 'EXECUTOR', 'SCHEDULED')),
    CONSTRAINT ck_verification_run_status CHECK (status IN ('PENDING', 'RUNNING', 'PASSED', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_verification_run_plan ON verification_run (plan_id, category);

CREATE TABLE verification_evidence (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES verification_plan(id),
    run_id UUID REFERENCES verification_run(id),
    kind VARCHAR(24) NOT NULL,
    source VARCHAR(20) NOT NULL,
    category VARCHAR(64),
    head_sha VARCHAR(128) NOT NULL,
    conclusion VARCHAR(16) NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    redacted_payload JSONB NOT NULL,
    artifact_path TEXT,
    artifact_sha256 VARCHAR(64),
    collected_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    purged_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_verification_evidence_kind CHECK (kind IN ('CHECK_RUN', 'EXECUTOR_ARTIFACT', 'SCHEDULED_RUN', 'GATE_NOTE')),
    CONSTRAINT ck_verification_evidence_source CHECK (source IN ('PR_CHECK', 'POST_MERGE_CHECK', 'EXECUTOR', 'SCHEDULED')),
    CONSTRAINT ck_verification_evidence_conclusion CHECK (conclusion IN ('SUCCESS', 'FAILURE', 'NEUTRAL'))
);

CREATE INDEX idx_verification_evidence_plan ON verification_evidence (plan_id, head_sha);
CREATE INDEX idx_verification_evidence_retention ON verification_evidence (expires_at) WHERE purged_at IS NULL;

CREATE TABLE verification_node (
    id UUID PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    node_type VARCHAR(16) NOT NULL,
    path VARCHAR(512) NOT NULL,
    name VARCHAR(128) NOT NULL,
    baseline_commit VARCHAR(128) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_verification_node UNIQUE (project_id, node_type, path, baseline_commit),
    CONSTRAINT ck_verification_node_type CHECK (node_type IN ('FILE', 'MODULE', 'CAPABILITY', 'TEST_SUITE'))
);

CREATE TABLE verification_edge (
    id UUID PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    from_node_id UUID NOT NULL REFERENCES verification_node(id),
    to_node_id UUID NOT NULL REFERENCES verification_node(id),
    provenance VARCHAR(8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_verification_edge UNIQUE (from_node_id, to_node_id, provenance),
    CONSTRAINT ck_verification_edge_provenance CHECK (provenance IN ('STATIC', 'HUMAN', 'LLM', 'SYSTEM'))
);

CREATE INDEX idx_verification_edge_from ON verification_edge (from_node_id);
CREATE INDEX idx_verification_edge_to ON verification_edge (to_node_id);

-- ADR-0009: selection state per project, including the recall circuit breaker.
CREATE TABLE verification_selection_state (
    project_id VARCHAR(64) PRIMARY KEY,
    subtractive_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    consecutive_full_recall_runs INTEGER NOT NULL DEFAULT 0,
    breaker_open BOOLEAN NOT NULL DEFAULT FALSE,
    breaker_reason TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
