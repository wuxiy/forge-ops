package com.company.forgeops.v2.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.company.forgeops.v2.feedback.domain.ContextSnapshotRepository;
import com.company.forgeops.v2.feedback.domain.FeedbackRepository;
import com.company.forgeops.v2.security.ProjectTokenService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Explicit localhost HTTP test against a disposable PostgreSQL database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"forgeops.v2.security.token-secret=integration-test-security-secret",
                "forgeops.v2.registry.path=src/test/resources/v2-registry", "forgeops.v2.registry.workspace-root=."})
class FeedbackSecurityPostgresIT {

    private static final String PROJECT_A = "security-project-a";

    @LocalServerPort
    private int port;

    @Autowired
    private ProjectTokenService tokens;

    @Autowired
    private FeedbackRepository feedbacks;

    @Autowired
    private ContextSnapshotRepository snapshots;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void rejectsMissingWrongProjectAndCrossReporterAccessWhilePersistingOnlyRedactedContext() throws Exception {
        String payload = "{\"title\":\"Checkout failure token=super-secret-token-123\","
                + "\"description\":\"contact test.user@example.com Authorization: Bearer super-secret-token-123\","
                + "\"browserContext\":{\"url\":\"https://app.test/pay?token=super-secret-token-123\"},"
                + "\"traceId\":\"security-it\"}";

        HttpResponse<String> preflight = options(PROJECT_A, "https://unregistered.example");
        assertEquals(403, preflight.statusCode());
        assertFalse(preflight.headers().firstValue("Access-Control-Allow-Origin").isPresent());
        HttpResponse<String> allowedPreflight = options(PROJECT_A, "https://app.test");
        assertEquals(204, allowedPreflight.statusCode());
        assertEquals("https://app.test", allowedPreflight.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertEquals(401, post(PROJECT_A, null, payload).statusCode());
        String wrongProject = tokens.issue("user-a", "security-project-b", Set.of("feedback:write"));
        assertEquals(403, post(PROJECT_A, wrongProject, payload).statusCode());
        String wrongScope = tokens.issue("user-a", PROJECT_A, Set.of("feedback:read"));
        assertEquals(403, post(PROJECT_A, wrongScope, payload).statusCode());

        String owner = tokens.issue("user-a", PROJECT_A, Set.of("feedback:write", "feedback:read", "feedback:reopen"));
        assertEquals(201, post(PROJECT_A, owner, payload).statusCode());

        var feedback = feedbacks.findByProjectIdAndReporterSubjectOrderByCreatedAtDesc(PROJECT_A, "user-a").stream()
                .findFirst().orElseThrow();
        assertFalse(feedback.getDescription().contains("super-secret-token-123"));
        var snapshot = snapshots.findByCycleIdOrderByCreatedAtAsc(feedback.getCurrentCycleId()).getFirst();
        assertFalse(snapshot.getContentJson().contains("super-secret-token-123"));
        assertFalse(snapshot.getContentJson().contains("test.user@example.com"));

        String otherReporter = tokens.issue("user-b", PROJECT_A, Set.of("feedback:read"));
        assertEquals(403, get(PROJECT_A, feedback.getId().toString(), otherReporter).statusCode());
        HttpResponse<String> ownerRead = get(PROJECT_A, feedback.getId().toString(), owner);
        assertEquals(200, ownerRead.statusCode());
        assertNotNull(ownerRead.body());
    }

    private HttpResponse<String> post(String projectId, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(projectId)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String projectId, String feedbackId, String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(uri(projectId) + "/" + feedbackId))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> options(String projectId, String origin) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(projectId)).header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String projectId) {
        return URI.create("http://127.0.0.1:" + port + "/api/v2/projects/" + projectId + "/feedback");
    }
}
