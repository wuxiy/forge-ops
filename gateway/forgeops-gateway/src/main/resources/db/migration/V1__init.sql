-- ForgeOps Gateway V0.1 schema（架构文档 §18）

CREATE SEQUENCE forgeops_feedback_seq START 1000;

CREATE TABLE forgeops_feedback (
    id                  BIGINT PRIMARY KEY DEFAULT nextval('forgeops_feedback_seq'),
    project_id          VARCHAR(64)  NOT NULL,
    type                VARCHAR(20)  NOT NULL,
    title               VARCHAR(200) NOT NULL,
    description         TEXT         NOT NULL,
    expected_behavior   TEXT,
    steps               TEXT,
    note                TEXT,
    reporter_id         VARCHAR(64),
    reporter_name       VARCHAR(64)  NOT NULL,
    environment         VARCHAR(20)  NOT NULL,
    page_url            VARCHAR(512),
    status              VARCHAR(32)  NOT NULL,
    frontend_version    VARCHAR(64),
    frontend_commit     VARCHAR(64),
    backend_version     VARCHAR(64),
    backend_commit      VARCHAR(64),
    multica_issue_id    VARCHAR(64),
    multica_issue_url   VARCHAR(256),
    pr_url              VARCHAR(512),
    ci_pipeline_id      VARCHAR(64),
    deployment_version  VARCHAR(64),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_feedback_reporter ON forgeops_feedback (reporter_name, project_id, created_at DESC);
CREATE INDEX idx_feedback_status   ON forgeops_feedback (status);

CREATE TABLE forgeops_context (
    id              BIGSERIAL PRIMARY KEY,
    feedback_id     BIGINT NOT NULL REFERENCES forgeops_feedback (id),
    schema_version  VARCHAR(10) NOT NULL,
    reason          VARCHAR(32) NOT NULL DEFAULT 'INITIAL',
    context_json    JSONB  NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_context_feedback ON forgeops_context (feedback_id, created_at);

CREATE TABLE forgeops_feedback_comment (
    id          BIGSERIAL PRIMARY KEY,
    feedback_id BIGINT NOT NULL REFERENCES forgeops_feedback (id),
    author_id   VARCHAR(64),
    author_type VARCHAR(16) NOT NULL,
    content     TEXT NOT NULL,
    attachments JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_comment_feedback ON forgeops_feedback_comment (feedback_id, created_at);

CREATE TABLE forgeops_feedback_verification (
    id           BIGSERIAL PRIMARY KEY,
    feedback_id  BIGINT NOT NULL REFERENCES forgeops_feedback (id),
    verifier_id  VARCHAR(64),
    verifier_name VARCHAR(64) NOT NULL,
    result       VARCHAR(10) NOT NULL,
    comment      TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE forgeops_integration_event (
    id                BIGSERIAL PRIMARY KEY,
    feedback_id       BIGINT REFERENCES forgeops_feedback (id),
    source            VARCHAR(32) NOT NULL,
    external_event_id VARCHAR(128) NOT NULL,
    event_type        VARCHAR(64) NOT NULL,
    payload           JSONB,
    status            VARCHAR(16) NOT NULL DEFAULT 'PROCESSED',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_integration_event UNIQUE (source, external_event_id)
);

CREATE TABLE forgeops_audit_log (
    id          BIGSERIAL PRIMARY KEY,
    feedback_id BIGINT,
    actor       VARCHAR(64) NOT NULL,
    action      VARCHAR(64) NOT NULL,
    detail      JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_feedback ON forgeops_audit_log (feedback_id, created_at);
