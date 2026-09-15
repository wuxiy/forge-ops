package com.company.forgeops.v2.integration.github;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** GitHub.com REST reader for one configured repository and pull request number. */
@Component
public class GitHubHttpPullRequestClient implements GitHubPullRequestClient {

    private final GitHubWebhookProperties properties;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GitHubHttpPullRequestClient(GitHubWebhookProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    @Override
    public ObservedPullRequest getPullRequest(String repository, long number) {
        if (!properties.isEnabled() || properties.getApiToken() == null || properties.getApiToken().isBlank()) {
            throw new GitHubProviderUnavailableException("GitHub evidence query is not configured");
        }
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") || number < 1) {
            throw new IllegalArgumentException("Invalid GitHub pull request reference");
        }
        URI endpoint = URI.create(properties.getApiBaseUrl().toString().replaceAll("/$", "") + "/repos/" + repository
                + "/pulls/" + number);
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "ForgeOps-2.0")
                .header("Authorization", "Bearer " + properties.getApiToken())
                .GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return new ObservedPullRequest(repository, number, null, "NOT_FOUND", false, null, null, null, false, null);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GitHubProviderUnavailableException("GitHub PR query returned HTTP " + response.statusCode());
            }
            return parse(repository, number, response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GitHubProviderUnavailableException("GitHub PR query was interrupted", interrupted);
        } catch (java.io.IOException failure) {
            throw new GitHubProviderUnavailableException("GitHub PR query failed", failure);
        }
    }

    private ObservedPullRequest parse(String repository, long expectedNumber, String body) {
        try {
            JsonNode root = json.readTree(body);
            long number = positiveNumber(root, "number");
            if (number != expectedNumber) {
                throw new GitHubProviderUnavailableException("GitHub PR response number does not match the request");
            }
            return new ObservedPullRequest(repository, number, text(root, "html_url"), text(root, "state"), bool(root, "draft"),
                    text(root, "head", "ref"), text(root, "head", "sha"), text(root, "base", "ref"), bool(root, "merged"),
                    nullableText(root, "merged_by", "login"));
        } catch (JacksonException invalid) {
            throw new GitHubProviderUnavailableException("GitHub PR response is not valid JSON", invalid);
        }
    }

    private static String text(JsonNode root, String... path) {
        JsonNode value = value(root, path);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new GitHubProviderUnavailableException("GitHub PR response is missing " + String.join(".", path));
        }
        return value.asString();
    }

    private static String nullableText(JsonNode root, String... path) {
        JsonNode value = value(root, path);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString() || value.asString().isBlank()) {
            throw new GitHubProviderUnavailableException("GitHub PR response has invalid " + String.join(".", path));
        }
        return value.asString();
    }

    private static long positiveNumber(JsonNode root, String... path) {
        JsonNode value = value(root, path);
        if (value == null || !value.isNumber() || value.longValue() < 1) {
            throw new GitHubProviderUnavailableException("GitHub PR response has invalid " + String.join(".", path));
        }
        return value.longValue();
    }

    private static boolean bool(JsonNode root, String... path) {
        JsonNode value = value(root, path);
        if (value == null || !value.isBoolean()) {
            throw new GitHubProviderUnavailableException("GitHub PR response has invalid " + String.join(".", path));
        }
        return value.asBoolean();
    }

    private static JsonNode value(JsonNode root, String... path) {
        JsonNode value = root;
        for (String part : path) {
            if (value == null || !value.isObject()) {
                return null;
            }
            value = value.get(part);
        }
        return value;
    }
}
