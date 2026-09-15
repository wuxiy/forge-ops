package com.company.forgeops.v2.integration.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.forgeops.v2.integration.domain.IntegrationEventState;
import com.company.forgeops.v2.integration.inbox.InboundEvent;
import com.company.forgeops.v2.integration.inbox.InboxReceipt;
import com.company.forgeops.v2.integration.inbox.IntegrationInbox;
import com.company.forgeops.v2.registry.ProjectCatalog;
import com.company.forgeops.v2.registry.ResolvedProject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class GitHubWebhookControllerTest {

    @Test
    void signedKnownRepositoryDeliveryBecomesOneDeferredMinimizedInboxFact() throws Exception {
        String secret = "test-webhook-secret";
        byte[] body = """
                {"action":"opened","number":42,"repository":{"full_name":"example/pilot"},
                 "pull_request":{"base":{"ref":"main"},"head":{"ref":"forgeops/v2-42","sha":"abc123"},
                 "draft":true,"merged":false,"merged_by":null,"body":"do not store this"}}
                """.getBytes(StandardCharsets.UTF_8);
        GitHubWebhookProperties properties = new GitHubWebhookProperties();
        properties.setEnabled(true);
        properties.setWebhookSecret(secret);
        ProjectCatalog catalog = Mockito.mock(ProjectCatalog.class);
        IntegrationInbox inbox = Mockito.mock(IntegrationInbox.class);
        when(catalog.resolveGitHubRepository("example/pilot")).thenReturn(Optional.of(project("pilot")));
        UUID eventId = UUID.randomUUID();
        when(inbox.accept(any())).thenReturn(new InboxReceipt(eventId, IntegrationEventState.DEFERRED, false));
        GitHubWebhookController controller = new GitHubWebhookController(properties, new GitHubWebhookSignature(),
                new GitHubWebhookNormalizer(JsonMapper.shared()), catalog, inbox);

        var response = controller.accept("delivery-42", "pull_request", signature(secret, body), body);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        ArgumentCaptor<InboundEvent> event = ArgumentCaptor.forClass(InboundEvent.class);
        verify(inbox).accept(event.capture());
        assertEquals("GITHUB", event.getValue().source());
        assertEquals("delivery-42", event.getValue().externalEventId());
        assertEquals("PULL_REQUEST", event.getValue().eventType());
        assertEquals("pilot", event.getValue().projectId());
        assertEquals("github:delivery-42", event.getValue().traceId());
        org.junit.jupiter.api.Assertions.assertFalse(event.getValue().payloadJson().contains("do not store this"));
    }

    @Test
    void disabledOrBadlySignedEndpointsFailBeforeAnyInboxWrite() {
        GitHubWebhookProperties properties = new GitHubWebhookProperties();
        ProjectCatalog catalog = Mockito.mock(ProjectCatalog.class);
        IntegrationInbox inbox = Mockito.mock(IntegrationInbox.class);
        GitHubWebhookController controller = new GitHubWebhookController(properties, new GitHubWebhookSignature(),
                new GitHubWebhookNormalizer(JsonMapper.shared()), catalog, inbox);

        ResponseStatusException disabled = assertThrows(ResponseStatusException.class,
                () -> controller.accept("delivery", "ping", "sha256=00", new byte[0]));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, disabled.getStatusCode());

        properties.setEnabled(true);
        properties.setWebhookSecret("secret");
        ResponseStatusException invalid = assertThrows(ResponseStatusException.class,
                () -> controller.accept("delivery", "ping", "sha256=00", new byte[0]));
        assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
    }

    private static ResolvedProject project(String id) {
        return new ResolvedProject(id, Path.of("."), List.of(Path.of(".")), Set.of("https://pilot.example"),
                new ResolvedProject.GitHubDelivery("example/pilot", "main", Set.of("owner"), "test"));
    }

    private static String signature(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder hex = new StringBuilder("sha256=");
        for (byte value : mac.doFinal(body)) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }
}
