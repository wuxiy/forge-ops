package com.company.forgeops.multica.client;

import com.company.forgeops.context.builder.ContextPackJson;
import com.company.forgeops.feedback.domain.Feedback;
import com.company.forgeops.project.registry.ProjectConfig;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Multica Issue 编排（§13）：一个反馈原则上对应一个主 Issue；
 * Gateway 不复制 Multica 工作数据，只保存 multica_issue_id 映射。
 */
@Service
public class MulticaIssueService {

    private final MulticaClient client;

    public MulticaIssueService(MulticaClient client) {
        this.client = client;
    }

    /** Issue 内容模板（§13.1）。 */
    public String renderIssueBody(Feedback feedback, java.util.Map<String, Object> contextPack, ProjectConfig project) {
        StringBuilder body = new StringBuilder();
        body.append("# Feedback\n\n");
        body.append(feedback.getDescription()).append("\n");
        if (feedback.getSteps() != null && !feedback.getSteps().isBlank()) {
            body.append("\n操作步骤：\n").append(feedback.getSteps()).append("\n");
        }
        if (feedback.getNote() != null && !feedback.getNote().isBlank()) {
            body.append("\n补充说明：\n").append(feedback.getNote()).append("\n");
        }

        body.append("\n# Environment\n\n");
        body.append("project: ").append(project.id()).append("\n");
        body.append("env: ").append(feedback.getEnvironment()).append("\n");
        body.append("frontend: ").append(nullable(feedback.getFrontendVersion())).append(" / ")
                .append(nullable(feedback.getFrontendCommit())).append("\n");
        body.append("backend: ").append(nullable(feedback.getBackendVersion())).append(" / ")
                .append(nullable(feedback.getBackendCommit())).append("\n");
        body.append("page: ").append(nullable(feedback.getPageUrl())).append("\n");

        Object requests = contextPack.get("requests");
        if (requests instanceof java.util.List<?> list && !list.isEmpty()) {
            body.append("\n# Failed Requests\n\n");
            for (Object item : list) {
                if (item instanceof java.util.Map<?, ?> m) {
                    body.append(m.get("method")).append(" ").append(m.get("url")).append("\n");
                    body.append("status: ").append(m.get("status")).append("\n");
                    if (m.get("requestId") != null) body.append("requestId: ").append(m.get("requestId")).append("\n");
                    body.append("\n");
                }
            }
        }

        Object logs = contextPack.get("logs");
        if (logs instanceof java.util.List<?> logList && !logList.isEmpty()) {
            body.append("# Related Logs\n\n");
            for (Object item : logList) {
                if (item instanceof java.util.Map<?, ?> m && m.get("entries") instanceof java.util.List<?> entries) {
                    body.append("service=").append(m.get("source")).append(" requestId=").append(m.get("requestId")).append("\n");
                    String lines = (String) entries.stream().map(String::valueOf).collect(Collectors.joining("\n"));
                    body.append("```json\n").append(lines).append("\n```\n\n");
                }
            }
        }

        body.append("# Context Pack\n\n```json\n");
        body.append(ContextPackJson.toJson(contextPack));
        body.append("\n```\n");

        Object criteria = contextPack.get("acceptanceCriteria");
        if (criteria instanceof java.util.List<?> criteriaList && !criteriaList.isEmpty()) {
            body.append("\n# Acceptance Criteria\n\n");
            for (int i = 0; i < criteriaList.size(); i++) {
                body.append(i + 1).append(". ").append(criteriaList.get(i)).append("\n");
            }
        }

        body.append("\n---\n\n来源反馈: ").append(feedback.identifier()).append("\n");
        return body.toString();
    }

    private String nullable(String value) {
        return value == null ? "-" : value;
    }
}
