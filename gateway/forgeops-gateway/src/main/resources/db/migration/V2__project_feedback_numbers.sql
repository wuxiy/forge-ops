-- V2：反馈编号按项目区分（项目前缀 + 独立序列）
-- 标识格式：<PREFIX>-FB-<display_no>（如 ADB-FB-1001）；历史/未配置前缀项目保持 FB-<display_no>
-- 内部自增主键 id 不变（FK/审计兼容），对外标识由 prefix+display_no 组成。

ALTER TABLE forgeops_feedback ADD COLUMN feedback_prefix VARCHAR(16);
ALTER TABLE forgeops_feedback ADD COLUMN display_no BIGINT;

-- 历史数据回填：display_no = id，prefix 保持 NULL（旧格式 FB-1002 等）
UPDATE forgeops_feedback SET display_no = id WHERE display_no IS NULL;

-- 对外标识唯一性：(prefix, display_no)
CREATE UNIQUE INDEX uq_feedback_identifier
    ON forgeops_feedback ((COALESCE(feedback_prefix, '')), display_no);

-- 每项目独立序列（key 用 prefix 短码；未配前缀的项目走全局 forgeops_feedback_seq 旧行为）
CREATE TABLE forgeops_project_sequence (
    prefix     VARCHAR(16) PRIMARY KEY,
    last_value BIGINT NOT NULL DEFAULT 1000
);
