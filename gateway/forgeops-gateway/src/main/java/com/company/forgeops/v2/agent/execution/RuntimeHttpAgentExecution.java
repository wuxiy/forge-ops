package com.company.forgeops.v2.agent.execution;

import com.company.forgeops.v2.agent.domain.AgentRunState;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** HTTP adapter for the loopback/private Runtime. It has no workflow decisions and no provider parsing. */
@Service
public class RuntimeHttpAgentExecution implements AgentExecution {

    private final RuntimeExecutionProperties properties;
    private final ObjectMapper json;
    private final HttpClient client;

    public RuntimeHttpAgentExecution(RuntimeExecutionProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis())).build();
    }

    @Override
    public RuntimeRunSnapshot submit(RuntimeRunRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", request.idempotencyKey());
        body.put("projectId", request.projectId());
        body.put("role", request.role().name());
        body.put("cwd", request.cwd().toString());
        body.put("prompt", request.prompt());
        body.put("outputSchema", request.outputSchema());
        body.put("timeoutMs", request.timeoutMillis());
        return exchange("POST", "/v1/runs", body);
    }

    @Override
    public RuntimeRunSnapshot inspect(String idempotencyKey) {
        return exchange("GET", "/v1/runs/" + encodePath(idempotencyKey), null);
    }

    @Override
    public RuntimeRunSnapshot cancel(String idempotencyKey) {
        return exchange("POST", "/v1/runs/" + encodePath(idempotencyKey), null);
    }

    private RuntimeRunSnapshot exchange(String method, String path, Map<String, Object> requestBody) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint(path))
                    .timeout(Duration.ofMillis(properties.getRequestTimeoutMillis()))
                    .header("Authorization", "Bearer " + properties.getServiceToken())
                    .header("Accept", "application/json");
            if (requestBody != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(requestBody)));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new RuntimeUnavailableException("Runtime did not retain the requested execution");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeUnavailableException("Runtime rejected the internal execution request: HTTP " + response.statusCode());
            }
            return parse(response.body());
        } catch (JacksonException error) {
            throw new IllegalArgumentException("Runtime request cannot be serialized", error);
        } catch (java.io.IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeUnavailableException("Runtime is unavailable", error);
        }
    }

    private RuntimeRunSnapshot parse(String body) {
        try {
            JsonNode node = json.readTree(body);
            String key = requiredText(node, "idempotencyKey");
            String state = requiredText(node, "state");
            return new RuntimeRunSnapshot(key, optionalText(node, "providerRunId"), AgentRunState.valueOf(state),
                    optionalText(node, "failureCategory"), optionalJson(node, "resultJson"), optionalText(node, "resultError"));
        } catch (JacksonException | IllegalArgumentException error) {
            throw new RuntimeUnavailableException("Runtime returned an invalid execution response", error);
        }
    }

    private URI endpoint(String path) {
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        return URI.create(base + path);
    }

    private static String encodePath(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Runtime response lacks " + field);
        }
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static String optionalJson(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
