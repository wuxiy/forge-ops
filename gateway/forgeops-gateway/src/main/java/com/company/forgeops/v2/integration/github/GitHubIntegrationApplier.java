package com.company.forgeops.v2.integration.github;

import com.company.forgeops.v2.integration.domain.IntegrationEvent;
import com.company.forgeops.v2.integration.inbox.IntegrationApplier;
import com.company.forgeops.v2.registry.ProjectCatalog;
import com.company.forgeops.v2.verification.domain.DeliveryEvidence;
import com.company.forgeops.v2.verification.domain.DeliveryEvidenceRepository;
import com.company.forgeops.v2.verification.domain.DeliveryEvidenceState;
import com.company.forgeops.v2.workflow.FeedbackWorkflow;
import java.util.List;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Applies only signed, normalized GitHub merge facts. CI and deployment facts remain explicitly deferred. */
@Component
public class GitHubIntegrationApplier implements IntegrationApplier {

    private static final Set<String> PULL_REQUEST_FIELDS = Set.of("payloadSha256", "repository", "action", "number",
            "baseBranch", "headBranch", "headSha", "draft", "merged", "mergedBy");

    private final DeliveryEvidenceRepository evidence;
    private final ProjectCatalog catalog;
    private final FeedbackWorkflow workflow;
    private final ObjectMapper json;

    public GitHubIntegrationApplier(DeliveryEvidenceRepository evidence, ProjectCatalog catalog, FeedbackWorkflow workflow,
            ObjectMapper json) {
        this.evidence = evidence;
        this.catalog = catalog;
        this.workflow = workflow;
        this.json = json;
    }

    @Override
    public ApplyResult apply(IntegrationEvent event) {
        if (!"GITHUB".equals(event.getSource())) {
            return ApplyResult.deferred("No verified handler is registered for " + event.getSource());
        }
        if (!"PULL_REQUEST".equals(event.getEventType())) {
            return ApplyResult.deferred("No verified handler is registered for " + event.getEventType());
        }
        PullRequestDelivery delivery;
        try {
            delivery = parse(event.getPayloadJson());
        } catch (IllegalArgumentException invalid) {
            return ApplyResult.rejected("GitHub delivery fact is invalid");
        }
        if (!"closed".equals(delivery.action()) || !delivery.merged()) {
            return ApplyResult.applied();
        }
        var project = catalog.resolveGitHubRepository(delivery.repository()).orElse(null);
        if (project == null || !project.id().equals(event.getProjectId())) {
            return ApplyResult.rejected("GitHub repository does not match the registered project");
        }
        if (delivery.mergedBy() == null || !project.github().allowedMergeLogins().contains(delivery.mergedBy())) {
            return ApplyResult.rejected("GitHub merge actor is not authorized");
        }
        List<DeliveryEvidence> candidates = evidence.findByRepositoryAndPullRequestNoAndState(delivery.repository(),
                delivery.number(), DeliveryEvidenceState.VERIFIED);
        DeliveryEvidence match = candidates.stream().filter(item -> item.getFeedbackId().equals(event.getFeedbackId())
                || event.getFeedbackId() == null).filter(item -> item.getCycleId().equals(event.getCycleId())
                || event.getCycleId() == null).filter(item -> exactMatch(item, delivery)).findFirst().orElse(null);
        if (match == null) {
            return ApplyResult.deferred("Verified PR evidence is not available for this merge fact");
        }
        if (delivery.draft()) {
            return ApplyResult.rejected("A draft pull request cannot satisfy a merge event");
        }
        workflow.recordAuthorizedMerge(match.getFeedbackId(), match.getCycleId(), match.getAgentRunId(), delivery.mergedBy(),
                "github:" + event.getExternalEventId());
        return ApplyResult.applied();
    }

    private PullRequestDelivery parse(String payloadJson) {
        try {
            JsonNode root = json.readTree(payloadJson);
            Set<String> fields = new java.util.HashSet<>();
            if (root != null && root.isObject()) fields.addAll(root.propertyNames());
            if (!PULL_REQUEST_FIELDS.equals(fields)) throw new IllegalArgumentException("unexpected fields");
            return new PullRequestDelivery(text(root, "repository"), text(root, "action"), positiveNumber(root, "number"),
                    text(root, "baseBranch"), text(root, "headBranch"), text(root, "headSha"), bool(root, "draft"),
                    bool(root, "merged"), nullableText(root, "mergedBy"));
        } catch (JacksonException invalidJson) {
            throw new IllegalArgumentException("invalid JSON", invalidJson);
        }
    }

    private static boolean exactMatch(DeliveryEvidence expected, PullRequestDelivery event) {
        return expected.getExpectedBaseBranch().equals(event.baseBranch())
                && expected.getExpectedHeadBranch().equals(event.headBranch())
                && expected.getExpectedHeadSha().equals(event.headSha());
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) throw new IllegalArgumentException(field);
        return value.asString();
    }

    private static long positiveNumber(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isNumber() || value.longValue() < 1) throw new IllegalArgumentException(field);
        return value.longValue();
    }

    private static boolean bool(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isBoolean()) throw new IllegalArgumentException(field);
        return value.asBoolean();
    }

    private static String nullableText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isString() || value.asString().isBlank()) throw new IllegalArgumentException(field);
        return value.asString();
    }

    private record PullRequestDelivery(String repository, String action, long number, String baseBranch, String headBranch,
            String headSha, boolean draft, boolean merged, String mergedBy) {
    }
}
