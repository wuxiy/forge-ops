package com.company.forgeops.v2.integration.github;

import com.company.forgeops.v2.integration.inbox.InboundEvent;
import com.company.forgeops.v2.integration.inbox.InboxReceipt;
import com.company.forgeops.v2.integration.inbox.IntegrationInbox;
import com.company.forgeops.v2.registry.ProjectCatalog;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** GitHub-facing boundary: verifies bytes first, then persists a minimized, deduplicated delivery fact. */
@RestController
@RequestMapping("/integrations/github")
public class GitHubWebhookController {

    private final GitHubWebhookProperties properties;
    private final GitHubWebhookSignature signature;
    private final GitHubWebhookNormalizer normalizer;
    private final ProjectCatalog catalog;
    private final IntegrationInbox inbox;

    public GitHubWebhookController(GitHubWebhookProperties properties, GitHubWebhookSignature signature,
            GitHubWebhookNormalizer normalizer, ProjectCatalog catalog, IntegrationInbox inbox) {
        this.properties = properties;
        this.signature = signature;
        this.normalizer = normalizer;
        this.catalog = catalog;
        this.inbox = inbox;
    }

    @PostMapping
    public ResponseEntity<InboxReceipt> accept(@RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestHeader("X-GitHub-Event") String event,
            @RequestHeader("X-Hub-Signature-256") String signatureHeader,
            @RequestBody byte[] rawBody) {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GitHub integration is disabled");
        }
        if (!signature.matches(properties.getWebhookSecret(), signatureHeader, rawBody)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook signature is invalid");
        }
        if (deliveryId == null || deliveryId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub delivery id is required");
        }
        if ("ping".equals(event)) {
            return ResponseEntity.noContent().build();
        }
        GitHubWebhookNormalizer.NormalizedWebhook normalized;
        try {
            normalized = normalizer.normalize(event, rawBody);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "GitHub event cannot be normalized");
        }
        String projectId = catalog.resolveGitHubRepository(normalized.repository())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "GitHub repository is not registered"))
                .id();
        InboxReceipt receipt = inbox.accept(new InboundEvent("GITHUB", deliveryId, normalized.eventType(), projectId,
                null, null, null, normalized.payloadJson(), "github:" + deliveryId));
        return ResponseEntity.accepted().body(receipt);
    }
}
