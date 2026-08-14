package com.company.forgeops.multica.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Multica REST 客户端（Engineering Work / Agent Control Plane，§13）。
 * 鉴权：PAT Bearer Token；Gateway 只保存业务侧映射 forgeops_feedback.multica_issue_id。
 */
@Component
public class MulticaClient {

    private static final Logger log = LoggerFactory.getLogger(MulticaClient.class);

    private final MulticaProperties properties;
    private final RestClient restClient;

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

    public Issue createIssue(String title, String description, String assigneeId) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", description);
        body.put("status", "todo");
        if (assigneeId != null) {
            body.put("assignee_type", "agent");
            body.put("assignee_id", assigneeId);
        }
        JsonNode response = restClient.post()
                .uri(uri -> uri.path("/api/issues").queryParam("workspace_id", properties.workspaceId()).build())
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return toIssue(response);
    }

    public Issue getIssue(String issueId) {
        JsonNode response = restClient.get()
                .uri(uri -> uri.path("/api/issues/{id}").queryParam("workspace_id", properties.workspaceId()).build(issueId))
                .retrieve()
                .body(JsonNode.class);
        return toIssue(response);
    }

    public Issue updateIssue(String issueId, String status, String assigneeId) {
        Map<String, Object> body = new HashMap<>();
        if (status != null) body.put("status", status);
        if (assigneeId != null) {
            body.put("assignee_type", "agent");
            body.put("assignee_id", assigneeId);
        }
        JsonNode response = restClient.put()
                .uri(uri -> uri.path("/api/issues/{id}").queryParam("workspace_id", properties.workspaceId()).build(issueId))
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return toIssue(response);
    }

    public Comment addComment(String issueId, String content) {
        JsonNode response = restClient.post()
                .uri(uri -> uri.path("/api/issues/{id}/comments").queryParam("workspace_id", properties.workspaceId()).build(issueId))
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

    public List<Comment> listComments(String issueId) {
        JsonNode response = restClient.get()
                .uri(uri -> uri.path("/api/issues/{id}/comments").queryParam("workspace_id", properties.workspaceId()).build(issueId))
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

    /** 按名称解析 Agent ID（workspace 内）。 */
    public String findAgentIdByName(String agentName) {
        JsonNode response = restClient.get()
                .uri(uri -> uri.path("/api/agents").queryParam("workspace_id", properties.workspaceId()).build())
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
