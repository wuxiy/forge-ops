package com.company.forgeops.multica.client;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Multica REST 客户端（Engineering Work / Agent Control Plane，§13）。
 * 鉴权：PAT Bearer Token；Gateway 只保存业务侧映射 forgeops_feedback.multica_issue_id。
 *
 * workspace/project 配置化：所有方法显式传 workspaceId（由 Project Registry 的
 * multica.workspace 按名称解析，见 resolveWorkspaceId）；issue 可归属 multica project。
 */
@Component
public class MulticaClient {

    private static final Logger log = LoggerFactory.getLogger(MulticaClient.class);

    private final MulticaProperties properties;
    private final RestClient restClient;

    /** workspace 名称/slug -> id 解析缓存（负结果缓存 60s，避免启动期反复打 API）。 */
    private final Map<String, String> workspaceCache = new ConcurrentHashMap<>();

    public MulticaClient(MulticaProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.token())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public record Issue(
            String id,
            String identifier,
            String title,
            String status,
            String assigneeType,
            String assigneeId,
            String updatedAt) {
    }

    public record Comment(String id, String authorType, String authorId, String content, String createdAt) {
    }

    // ---- workspace / project 解析 ----

    /** 按名称或 slug 解析 workspace id；匹配不到时回退全局配置的 workspaceId。 */
    public String resolveWorkspaceId(String workspaceNameOrSlug) {
        if (workspaceNameOrSlug == null || workspaceNameOrSlug.isBlank()) {
            return properties.workspaceId();
        }
        return workspaceCache.computeIfAbsent(workspaceNameOrSlug, name -> {
            try {
                JsonNode response = restClient.get().uri("/api/workspaces").retrieve().body(JsonNode.class);
                if (response != null && response.isArray()) {
                    for (JsonNode ws : response) {
                        if (name.equals(ws.path("name").asText()) || name.equals(ws.path("slug").asText())) {
                            String id = ws.path("id").asText();
                            log.info("Multica workspace '{}' -> {}", name, id);
                            return id;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析 multica workspace '{}' 失败，回退全局配置: {}", name, e.getMessage());
            }
            return properties.workspaceId();
        });
    }

    /** 按标题解析 workspace 内 project id（未配置/未找到返回 null，issue 不归属 project）。 */
    public String resolveProjectId(String workspaceId, String projectTitle) {
        if (workspaceId == null || projectTitle == null || projectTitle.isBlank()) {
            return null;
        }
        try {
            JsonNode response = restClient.get()
                    .uri(uri -> uri.path("/api/workspaces/{ws}/projects").build(workspaceId))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode projects = response == null ? null
                    : response.isArray() ? response : response.path("projects");
            if (projects != null && projects.isArray()) {
                for (JsonNode p : projects) {
                    if (projectTitle.equals(p.path("title").asText())
                            || projectTitle.equals(p.path("name").asText())) {
                        String id = p.path("id").asText();
                        log.info("Multica project '{}' @{} -> {}", projectTitle, workspaceId, id);
                        return id;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析 multica project '{}' 失败（issue 将不归属 project）: {}", projectTitle, e.getMessage());
        }
        return null;
    }

    // ---- issue / comment ----

    public Issue createIssue(String workspaceId, String projectId, String title, String description, String assigneeId) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", description);
        body.put("status", "todo");
        if (projectId != null) {
            body.put("project_id", projectId);
        }
        if (assigneeId != null) {
            body.put("assignee_type", "agent");
            body.put("assignee_id", assigneeId);
        }
        JsonNode response = restClient.post()
                .uri(uri -> uri.path("/api/issues").queryParam("workspace_id", workspaceId).build())
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return toIssue(response);
    }

    public Issue getIssue(String workspaceId, String issueId) {
        JsonNode response = restClient.get()
                .uri(uri -> uri.path("/api/issues/{id}").queryParam("workspace_id", workspaceId).build(issueId))
                .retrieve()
                .body(JsonNode.class);
        return toIssue(response);
    }

    public Issue updateIssue(String workspaceId, String issueId, String status, String assigneeId) {
        Map<String, Object> body = new HashMap<>();
        if (status != null) body.put("status", status);
        if (assigneeId != null) {
            body.put("assignee_type", "agent");
            body.put("assignee_id", assigneeId);
        }
        JsonNode response = restClient.put()
                .uri(uri -> uri.path("/api/issues/{id}").queryParam("workspace_id", workspaceId).build(issueId))
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return toIssue(response);
    }

    public Comment addComment(String workspaceId, String issueId, String content) {
        JsonNode response = restClient.post()
                .uri(uri -> uri.path("/api/issues/{id}/comments").queryParam("workspace_id", workspaceId).build(issueId))
                .body(Map.of("content", content))
                .retrieve()
                .body(JsonNode.class);
        return new Comment(
                response.path("id").asText(),
                response.path("author_type").asText(),
                response.path("author_id").asText(null),
                response.path("content").asText(),
                response.path("created_at").asText());
    }

    public List<Comment> listComments(String workspaceId, String issueId) {
        JsonNode response = restClient.get()
                .uri(uri -> uri.path("/api/issues/{id}/comments").queryParam("workspace_id", workspaceId).build(issueId))
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.isArray()) return List.of();
        List<Comment> comments = new java.util.ArrayList<>();
        for (JsonNode node : response) {
            comments.add(new Comment(
                    node.path("id").asText(),
                    node.path("author_type").asText(),
                    node.path("author_id").asText(null),
                    node.path("content").asText(),
                    node.path("created_at").asText()));
        }
        return comments;
    }

    /** 按名称解析 Agent ID（指定 workspace 内）。 */
    public String findAgentIdByName(String workspaceId, String agentName) {
        JsonNode response = restClient.get()
                .uri(uri -> uri.path("/api/agents").queryParam("workspace_id", workspaceId).build())
                .retrieve()
                .body(JsonNode.class);
        if (response == null) return null;
        JsonNode array = response.isArray() ? response : response.path("agents");
        if (!array.isArray()) return null;
        for (JsonNode agent : array) {
            if (agentName.equals(agent.path("name").asText())) {
                return agent.path("id").asText();
            }
        }
        return null;
    }

    public String issueUrl(String issueId) {
        return properties.appUrl() + "/issue/" + issueId;
    }

    private Issue toIssue(JsonNode node) {
        if (node == null) return null;
        return new Issue(
                node.path("id").asText(),
                node.path("identifier").asText(),
                node.path("title").asText(),
                node.path("status").asText(),
                node.path("assignee_type").asText(null),
                node.path("assignee_id").asText(null),
                node.path("updated_at").asText());
    }
}
