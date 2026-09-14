-- ForgeOps 2.0 creates a new forgeops_v2 database. No V0.1 table or state is reused.

CREATE TABLE project_feedback_counter (
    project_id VARCHAR(64) PRIMARY KEY,
    last_value BIGINT NOT NULL DEFAULT 1000,
    CONSTRAINT ck_project_feedback_counter_positive CHECK (last_value >= 1000)
);

CREATE TABLE feedback (
    id UUID PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    display_no BIGINT NOT NULL,
    reporter_subject VARCHAR(128) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    current_cycle_id UUID NOT NULL,
    state VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_feedback_project_display_no UNIQUE (project_id, display_no)
);

CREATE INDEX idx_feedback_project_reporter ON feedback (project_id, reporter_subject, created_at DESC);
CREATE INDEX idx_feedback_state ON feedback (state, updated_at);

CREATE TABLE feedback_cycle (
    id UUID PRIMARY KEY,
    feedback_id UUID NOT NULL REFERENCES feedback(id),
    cycle_no INTEGER NOT NULL,
    opened_by VARCHAR(128) NOT NULL,
    reopen_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_feedback_cycle_no UNIQUE (feedback_id, cycle_no)
);

ALTER TABLE feedback
    ADD CONSTRAINT fk_feedback_current_cycle
    FOREIGN KEY (current_cycle_id) REFERENCES feedback_cycle(id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE context_snapshot (
    id UUID PRIMARY KEY,
    feedback_id UUID NOT NULL REFERENCES feedback(id),
    cycle_id UUID NOT NULL REFERENCES feedback_cycle(id),
    schema_version VARCHAR(32) NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    redaction_count INTEGER NOT NULL DEFAULT 0,
    content_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_context_snapshot_cycle_hash UNIQUE (cycle_id, content_sha256)
);

CREATE TABLE agent_run (
    id UUID PRIMARY KEY,
    feedback_id UUID NOT NULL REFERENCES feedback(id),
    cycle_id UUID NOT NULL REFERENCES feedback_cycle(id),
    role VARCHAR(16) NOT NULL,
    attempt INTEGER NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    provider_run_id VARCHAR(128),
    state VARCHAR(32) NOT NULL,
    failure_category VARCHAR(64),
    failure_message TEXT,
    result_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_agent_run_attempt UNIQUE (cycle_id, role, attempt),
    CONSTRAINT uq_agent_run_idempotency UNIQUE (idempotency_key)
);

CREATE UNIQUE INDEX uq_agent_run_current_role
    ON agent_run (cycle_id, role)
    WHERE state IN ('QUEUED', 'RUNNING');
CREATE UNIQUE INDEX uq_agent_run_provider_id
    ON agent_run (provider_run_id)
    WHERE provider_run_id IS NOT NULL;

CREATE TABLE integration_event (
    id UUID PRIMARY KEY,
    source VARCHAR(32) NOT NULL,
    external_event_id VARCHAR(256) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    feedback_id UUID REFERENCES feedback(id),
    cycle_id UUID REFERENCES feedback_cycle(id),
    agent_run_id UUID REFERENCES agent_run(id),
    payload_json JSONB NOT NULL,
    state VARCHAR(16) NOT NULL,
    apply_attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_integration_event_source_external_id UNIQUE (source, external_event_id)
);

CREATE INDEX idx_integration_event_reconcile ON integration_event (state, updated_at);

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL UNIQUE,
    payload_json JSONB NOT NULL,
    state VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_event_dispatch ON outbox_event (state, next_attempt_at);

CREATE TABLE verification_record (
    id UUID PRIMARY KEY,
    feedback_id UUID NOT NULL REFERENCES feedback(id),
    cycle_id UUID NOT NULL REFERENCES feedback_cycle(id),
    verifier_subject VARCHAR(128) NOT NULL,
    result VARCHAR(16) NOT NULL,
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    feedback_id UUID REFERENCES feedback(id),
    cycle_id UUID REFERENCES feedback_cycle(id),
    agent_run_id UUID REFERENCES agent_run(id),
    actor VARCHAR(128) NOT NULL,
    action VARCHAR(96) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    trace_id VARCHAR(128),
    detail_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_feedback ON audit_log (feedback_id, created_at);
