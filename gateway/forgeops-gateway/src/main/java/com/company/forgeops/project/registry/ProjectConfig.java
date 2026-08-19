package com.company.forgeops.project.registry;

import java.util.Map;

/**
 * Project Registry 单项目配置（§10），YAML 加载。
 * 字段与 registry/projects/*.yaml 一一对应。
 */
public record ProjectConfig(
        String id,
        String name,
        Map<String, Object> feedback,
        RepoConfig frontend,
        RepoConfig backend,
        Map<String, Object> runtime,
        Observability observability,
        MulticaConfig multica,
        Map<String, Object> ci,
        Map<String, Object> environments,
        Map<String, Object> policy,
        Map<String, Object> security) {

    public record RepoConfig(String framework, String repo, String repoProvider, String path, String defaultBranch) {
    }

    public record Observability(Logs logs) {
        public record Logs(String type, String service, String baseUrl) {
        }
    }

    public record MulticaConfig(String workspace, String project, String triageAgent, String codingAgent) {
    }

    /** 反馈编号项目短码（如 ADB），未配置返回 null（旧式全局 FB-n）。 */
    public String feedbackPrefix() {
        Object v = feedback == null ? null : feedback.get("prefix");
        return v instanceof String s && !s.isBlank() ? s.toUpperCase() : null;
    }

    public boolean policyFlag(String key, boolean fallback) {
        Object value = policy == null ? null : policy.get(key);
        return value instanceof Boolean b ? b : fallback;
    }
}
