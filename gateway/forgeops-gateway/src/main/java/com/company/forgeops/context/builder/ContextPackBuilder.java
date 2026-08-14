package com.company.forgeops.context.builder;

import com.company.forgeops.feedback.api.dto.FeedbackSubmissionDto;
import com.company.forgeops.feedback.domain.Feedback;
import com.company.forgeops.context.log.HttpLogFetcher;
import com.company.forgeops.policy.security.PiiSanitizer;
import com.company.forgeops.project.registry.ProjectConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Engineering Context Pack Builder（§11）：
 * SDK 提交 + Project Registry + 日志摘录 → 标准化上下文（schemaVersion 1.0），
 * 输出结构与 schemas/context-pack.schema.json 对齐，作为 Multica Issue 与 Agent 的唯一上下文。
 */
@Component
public class ContextPackBuilder {

    private final HttpLogFetcher logFetcher;
    private final PiiSanitizer sanitizer;

    public ContextPackBuilder(HttpLogFetcher logFetcher, PiiSanitizer sanitizer) {
        this.logFetcher = logFetcher;
        this.sanitizer = sanitizer;
    }

    public Map<String, Object> build(Feedback feedback, FeedbackSubmissionDto submission, ProjectConfig project) {
        return build(feedback, submission, project, null);
    }

    public Map<String, Object> build(Feedback feedback, FeedbackSubmissionDto submission, ProjectConfig project,
                                     String screenshotStoredPath) {
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("schemaVersion", "1.0");

        // feedback
        Map<String, Object> fb = new LinkedHashMap<>();
        fb.put("id", feedback.identifier());
        fb.put("type", feedback.getType());
        fb.put("title", sanitizer.sanitize(feedback.getTitle()));
        fb.put("description", sanitizer.sanitize(feedback.getDescription()));
        if (feedback.getExpectedBehavior() != null) fb.put("expectedBehavior", sanitizer.sanitize(feedback.getExpectedBehavior()));
        if (feedback.getSteps() != null) fb.put("steps", sanitizer.sanitize(feedback.getSteps()));
        if (feedback.getNote() != null) fb.put("note", sanitizer.sanitize(feedback.getNote()));
        fb.put("reporter", feedback.getReporterName());
        pack.put("feedback", fb);

        // project
        Map<String, Object> proj = new LinkedHashMap<>();
        proj.put("id", project.id());
        proj.put("name", project.name());
        proj.put("environment", feedback.getEnvironment());
        pack.put("project", proj);

        // page
        Map<String, Object> page = new LinkedHashMap<>();
        if (submission != null && submission.page() != null) {
            page.put("url", submission.page().url());
            page.put("route", submission.page().route());
            page.put("title", submission.page().title());
        } else if (feedback.getPageUrl() != null) {
            page.put("url", feedback.getPageUrl());
        }
        pack.put("page", page);

        // client
        if (submission != null && submission.client() != null) {
            Map<String, Object> client = new LinkedHashMap<>();
            client.put("userAgent", submission.client().userAgent());
            client.put("platform", submission.client().platform());
            client.put("screen", submission.client().screen());
            pack.put("client", client);
        }

        // versions
        Map<String, Object> frontend = new LinkedHashMap<>();
        frontend.put("version", feedback.getFrontendVersion());
        frontend.put("commit", feedback.getFrontendCommit());
        pack.put("frontend", frontend);

        Map<String, Object> backend = new LinkedHashMap<>();
        backend.put("version", feedback.getBackendVersion());
        backend.put("commit", feedback.getBackendCommit());
        pack.put("backend", backend);

        // requests（脱敏 URL）+ 按 requestId 拉日志摘录
        List<Map<String, Object>> requests = new ArrayList<>();
        List<Map<String, Object>> logs = new ArrayList<>();
        if (submission != null && submission.requests() != null) {
            for (FeedbackSubmissionDto.RequestSummary r : submission.requests()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("method", r.method());
                item.put("url", sanitizer.sanitize(r.url()));
                item.put("status", r.status());
                item.put("durationMs", r.durationMs());
                item.put("requestId", r.requestId());
                item.put("traceId", r.traceId());
                item.put("time", r.time());
                requests.add(item);

                HttpLogFetcher.LogExcerpt excerpt = logFetcher.fetch(project, r.requestId());
                if (excerpt != null) {
                    Map<String, Object> logEntry = new LinkedHashMap<>();
                    logEntry.put("source", excerpt.source());
                    logEntry.put("requestId", excerpt.requestId());
                    logEntry.put("entries", excerpt.entries());
                    logs.add(logEntry);
                }
            }
        }
        pack.put("requests", requests);
        pack.put("logs", logs);

        if (submission != null && submission.consoleErrors() != null && !submission.consoleErrors().isEmpty()) {
            pack.put("consoleErrors", submission.consoleErrors().stream().map(sanitizer::sanitize).toList());
        }

        // git
        Map<String, Object> git = new LinkedHashMap<>();
        String repoProvider = project.frontend() != null && project.frontend().repoProvider() != null
                ? project.frontend().repoProvider() : "other";
        git.put("repoProvider", repoProvider);
        if (project.frontend() != null) {
            git.put("frontendRepo", project.frontend().repo());
            git.put("frontendPath", project.frontend().path());
        }
        if (project.backend() != null) {
            git.put("backendRepo", project.backend().repo());
            git.put("backendPath", project.backend().path());
        }
        if (project.frontend() != null) {
            git.put("defaultBranch", project.frontend().defaultBranch());
        }
        pack.put("git", git);

        // acceptance criteria（从预期效果推导 + 固定契约）
        List<String> criteria = new ArrayList<>();
        if (feedback.getExpectedBehavior() != null && !feedback.getExpectedBehavior().isBlank()) {
            for (String line : feedback.getExpectedBehavior().split("\\n")) {
                if (!line.isBlank()) criteria.add(line.trim());
            }
        }
        pack.put("acceptanceCriteria", criteria);

        pack.put("attachments", screenshotStoredPath != null
                ? List.of(Map.of("kind", "screenshot", "storedPath", screenshotStoredPath))
                : List.of());

        return pack;
    }
}
