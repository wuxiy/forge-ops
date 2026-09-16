package com.company.forgeops.v2.integration.github;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Converts GitHub's large webhook body into a small, schema-checked delivery fact. */
@Component
public class GitHubWebhookNormalizer {

    private final ObjectMapper json;

    public GitHubWebhookNormalizer(ObjectMapper json) {
        this.json = json;
    }

    public NormalizedWebhook normalize(String event, byte[] rawBody) {
        if (event == null || event.isBlank() || rawBody == null) {
            throw new IllegalArgumentException("GitHub event and body are required");
        }
        try {
            JsonNode root = json.readTree(rawBody);
            String repository = text(root, "repository", "full_name");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("payloadSha256", sha256(rawBody));
            payload.put("repository", repository);
            switch (event) {
                case "pull_request" -> pullRequest(root, payload);
                case "check_run" -> checkRun(root, payload);
                case "deployment_status" -> deploymentStatus(root, payload);
                case "workflow_run" -> workflowRun(root, payload);
                default -> throw new IllegalArgumentException("Unsupported GitHub event: " + event);
            }
            return new NormalizedWebhook(event.toUpperCase().replace('-', '_'), repository, json.writeValueAsString(payload));
        } catch (JacksonException invalidJson) {
            throw new IllegalArgumentException("GitHub webhook body is not valid", invalidJson);
        }
    }

    private static void pullRequest(JsonNode root, Map<String, Object> payload) {
        payload.put("action", text(root, "action"));
        payload.put("number", positiveNumber(root, "number"));
        payload.put("baseBranch", text(root, "pull_request", "base", "ref"));
        payload.put("headBranch", text(root, "pull_request", "head", "ref"));
        payload.put("headSha", text(root, "pull_request", "head", "sha"));
        payload.put("draft", bool(root, "pull_request", "draft"));
        payload.put("merged", bool(root, "pull_request", "merged"));
        JsonNode mergedBy = value(root, "pull_request", "merged_by", "login");
        if (mergedBy != null && !mergedBy.isNull()) {
            if (!mergedBy.isString() || mergedBy.asString().isBlank()) {
                throw new IllegalArgumentException("pull_request.merged_by.login must be a non-blank string or null");
            }
            payload.put("mergedBy", mergedBy.asString());
        } else {
            payload.put("mergedBy", null);
        }
    }

    private static void checkRun(JsonNode root, Map<String, Object> payload) {
        payload.put("action", text(root, "action"));
        payload.put("checkRunId", positiveNumber(root, "check_run", "id"));
        payload.put("checkRunName", text(root, "check_run", "name"));
        payload.put("pullRequestNo", positiveNumber(root, "check_run", "pull_requests", "0", "number"));
        payload.put("headSha", text(root, "check_run", "head_sha"));
        payload.put("status", text(root, "check_run", "status"));
        nullableText(root, payload, "conclusion", "check_run", "conclusion");
    }

    /** ADR-0009 scheduled facts bind repo+branch+commit+workflow and carry no feedback binding. */
    private static void workflowRun(JsonNode root, Map<String, Object> payload) {
        payload.put("action", text(root, "action"));
        payload.put("workflowName", text(root, "workflow_run", "name"));
        payload.put("workflowId", positiveNumber(root, "workflow_run", "id"));
        payload.put("branch", text(root, "workflow_run", "head_branch"));
        payload.put("headSha", text(root, "workflow_run", "head_sha"));
        payload.put("status", text(root, "workflow_run", "status"));
        payload.put("runNumber", positiveNumber(root, "workflow_run", "run_number"));
        nullableText(root, payload, "conclusion", "workflow_run", "conclusion");
    }

    private static void deploymentStatus(JsonNode root, Map<String, Object> payload) {
        payload.put("action", text(root, "action"));
        payload.put("deploymentId", positiveNumber(root, "deployment", "id"));
        payload.put("deploymentStatusId", positiveNumber(root, "deployment_status", "id"));
        payload.put("pullRequestNo", positiveNumber(root, "deployment", "payload", "forgeopsPullRequestNo"));
        payload.put("headSha", text(root, "deployment", "sha"));
        payload.put("environment", text(root, "deployment", "environment"));
        payload.put("state", text(root, "deployment_status", "state"));
    }

    private static void nullableText(JsonNode root, Map<String, Object> payload, String target, String... path) {
        JsonNode node = value(root, path);
        if (node == null || node.isNull()) {
            payload.put(target, null);
            return;
        }
        if (!node.isString() || node.asString().isBlank()) {
            throw new IllegalArgumentException(String.join(".", path) + " must be a non-blank string or null");
        }
        payload.put(target, node.asString());
    }

    private static String text(JsonNode root, String... path) {
        JsonNode node = value(root, path);
        if (node == null || !node.isString() || node.asString().isBlank()) {
            throw new IllegalArgumentException(String.join(".", path) + " must be a non-blank string");
        }
        return node.asString();
    }

    private static long positiveNumber(JsonNode root, String... path) {
        JsonNode node = value(root, path);
        if (node == null || !node.isNumber() || node.longValue() < 1) {
            throw new IllegalArgumentException(String.join(".", path) + " must be a positive number");
        }
        return node.longValue();
    }

    private static boolean bool(JsonNode root, String... path) {
        JsonNode node = value(root, path);
        if (node == null || !node.isBoolean()) {
            throw new IllegalArgumentException(String.join(".", path) + " must be a boolean");
        }
        return node.asBoolean();
    }

    private static JsonNode value(JsonNode root, String... path) {
        JsonNode node = root;
        for (String part : path) {
            if (node == null) {
                return null;
            }
            if (node.isArray()) {
                try {
                    int index = Integer.parseInt(part);
                    if (index < 0 || index >= node.size()) {
                        return null;
                    }
                    node = node.get(index);
                    continue;
                } catch (NumberFormatException notAnIndex) {
                    return null;
                }
            }
            if (!node.isObject()) {
                return null;
            }
            node = node.get(part);
        }
        return node;
    }

    private static String sha256(byte[] body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    public record NormalizedWebhook(String eventType, String repository, String payloadJson) {
    }
}
