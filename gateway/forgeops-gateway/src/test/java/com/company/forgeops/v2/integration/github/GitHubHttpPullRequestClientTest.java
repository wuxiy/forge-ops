package com.company.forgeops.v2.integration.github;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GitHubHttpPullRequestClientTest {

    @Test
    void readsOnlyTheConfiguredPullRequestWithReadOnlyBearerCredentials() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/api/repos/example/pilot/pulls/42", exchange -> {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] body = """
                        {"number":42,"html_url":"https://github.com/example/pilot/pull/42","state":"open","draft":true,
                         "head":{"ref":"forgeops/v2-42","sha":"abc123"},"base":{"ref":"main"},"merged":false,
                         "merged_by":null}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            GitHubWebhookProperties properties = new GitHubWebhookProperties();
            properties.setEnabled(true);
            properties.setWebhookSecret("test-secret");
            properties.setApiToken("read-only-token");
            properties.setApiBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api"));

            var observed = new GitHubHttpPullRequestClient(properties, JsonMapper.shared()).getPullRequest("example/pilot", 42);

            assertEquals("Bearer read-only-token", authorization.get());
            assertEquals("forgeops/v2-42", observed.headBranch());
            assertEquals("abc123", observed.headSha());
            assertEquals("main", observed.baseBranch());
        } finally {
            server.stop(0);
        }
    }
}
