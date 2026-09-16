package com.company.forgeops.v2.integration.github;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** PUT /repos/{repo}/pulls/{n}/merge using the machine token only. */
@Component
public class GitHubHttpMergeClient implements GitHubMergeClient {

    private final GitHubWebhookProperties properties;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GitHubHttpMergeClient(GitHubWebhookProperties properties) {
        this.properties = properties;
    }

    @Override
    public void mergePullRequest(String repository, long number, String expectedHeadSha) {
        if (!properties.isEnabled() || properties.getMachineMergeToken() == null
                || properties.getMachineMergeToken().isBlank()) {
            throw new GitHubProviderUnavailableException("GitHub machine merge is not configured");
        }
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") || number < 1
                || expectedHeadSha == null || !expectedHeadSha.matches("[0-9a-f]{7,40}")) {
            throw new MergeRejected("INVALID_MERGE_REFERENCE");
        }
        URI endpoint = URI.create(properties.getApiBaseUrl().toString().replaceAll("/$", "") + "/repos/" + repository
                + "/pulls/" + number + "/merge");
        String body = "{\"merge_method\":\"squash\",\"sha\":\"" + expectedHeadSha + "\"}";
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "ForgeOps-2.0")
                .header("Authorization", "Bearer " + properties.getMachineMergeToken())
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return;
            }
            throw new MergeRejected("HTTP_" + response.statusCode());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GitHubProviderUnavailableException("GitHub merge was interrupted", interrupted);
        } catch (java.io.IOException failure) {
            throw new GitHubProviderUnavailableException("GitHub merge failed", failure);
        }
    }
}
