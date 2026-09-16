package com.company.forgeops.v2.integration.github;

import com.company.forgeops.v2.feedback.domain.Feedback;
import com.company.forgeops.v2.feedback.domain.FeedbackRepository;
import com.company.forgeops.v2.integration.domain.IntegrationEvent;
import com.company.forgeops.v2.integration.inbox.IntegrationApplier;
import com.company.forgeops.v2.registry.ProjectCatalog;
import com.company.forgeops.v2.registry.ResolvedProject;
import com.company.forgeops.v2.verification.domain.DeliveryEvidence;
import com.company.forgeops.v2.verification.domain.DeliveryEvidenceRepository;
import com.company.forgeops.v2.verification.domain.DeliveryEvidenceState;
import com.company.forgeops.v2.workflow.FeedbackState;
import com.company.forgeops.v2.workflow.FeedbackWorkflow;
import java.util.List;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Applies only schema-strict facts accepted by the signed GitHub webhook boundary. It never makes a provider call:
 * provider facts are already durable Inbox records before this class decides whether they can move the workflow.
 */
@Component
public class GitHubIntegrationApplier implements IntegrationApplier {

    private static final Set<String> PULL_REQUEST_FIELDS = Set.of("payloadSha256", "repository", "action", "number",
            "baseBranch", "headBranch", "headSha", "draft", "merged", "mergedBy");
    private static final Set<String> CHECK_RUN_FIELDS = Set.of("payloadSha256", "repository", "action", "checkRunId",
            "checkRunName", "pullRequestNo", "headSha", "status", "conclusion");
    private static final Set<String> DEPLOYMENT_STATUS_FIELDS = Set.of("payloadSha256", "repository", "action",
            "deploymentId", "deploymentStatusId", "pullRequestNo", "headSha", "environment", "state");

    private final DeliveryEvidenceRepository evidence;
    private final ProjectCatalog catalog;
    private final FeedbackWorkflow workflow;
    private final FeedbackRepository feedbacks;
    private final ObjectMapper json;

    public GitHubIntegrationApplier(DeliveryEvidenceRepository evidence, ProjectCatalog catalog, FeedbackWorkflow workflow,
            FeedbackRepository feedbacks, ObjectMapper json) {
        this.evidence = evidence;
        this.catalog = catalog;
        this.workflow = workflow;
        this.feedbacks = feedbacks;
        this.json = json;
    }

    @Override
    public ApplyResult apply(IntegrationEvent event) {
        if (!"GITHUB".equals(event.getSource())) {
            return ApplyResult.deferred("No verified handler is registered for " + event.getSource());
        }
        return switch (event.getEventType()) {
            case "PULL_REQUEST" -> applyPullRequest(event);
            case "CHECK_RUN" -> applyCheckRun(event);
            case "DEPLOYMENT_STATUS" -> applyDeploymentStatus(event);
            default -> ApplyResult.deferred("No verified handler is registered for " + event.getEventType());
        };
    }

    private ApplyResult applyPullRequest(IntegrationEvent event) {
        PullRequestDelivery delivery;
        try {
            delivery = parsePullRequest(event.getPayloadJson());
        } catch (IllegalArgumentException invalid) {
            return ApplyResult.rejected("GitHub pull request fact is invalid");
        }
        if (!"closed".equals(delivery.action()) || !delivery.merged()) {
            return ApplyResult.applied();
        }
        ResolvedProject project = registeredProject(event, delivery.repository());
        if (project == null) {
            return ApplyResult.rejected("GitHub repository does not match the registered project");
        }
        if (delivery.mergedBy() == null || !project.github().allowedMergeLogins().contains(delivery.mergedBy())) {
            return ApplyResult.rejected("GitHub merge actor is not authorized");
        }
        DeliveryEvidence match = verifiedEvidence(event, delivery.repository(), delivery.number(), delivery.headSha());
        if (match == null || !exactMatch(match, delivery)) {
            return ApplyResult.deferred("Verified PR evidence is not available for this merge fact");
        }
        if (delivery.draft()) {
            return ApplyResult.rejected("A draft pull request cannot satisfy a merge event");
        }
        Feedback feedback = currentFeedback(match);
        if (feedback == null) {
            return ApplyResult.rejected("GitHub merge belongs to an expired Feedback cycle");
        }
        if (feedback.getState() == FeedbackState.BUILD_RUNNING) {
            return ApplyResult.applied();
        }
        if (feedback.getState() == FeedbackState.CODE_RUNNING) {
            return ApplyResult.deferred("GitHub merge arrived before the verified PR became ready");
        }
        if (feedback.getState() != FeedbackState.PR_READY) {
            return ApplyResult.rejected("GitHub merge is not current for this Feedback state");
        }
        workflow.recordAuthorizedMerge(match.getFeedbackId(), match.getCycleId(), match.getAgentRunId(), delivery.mergedBy(),
                "github:" + event.getExternalEventId());
        return ApplyResult.applied();
    }

    private ApplyResult applyCheckRun(IntegrationEvent event) {
        CheckRunDelivery delivery;
        try {
            delivery = parseCheckRun(event.getPayloadJson());
        } catch (IllegalArgumentException invalid) {
            return ApplyResult.rejected("GitHub check run fact is invalid");
        }
        ResolvedProject project = registeredProject(event, delivery.repository());
        if (project == null) {
            return ApplyResult.rejected("GitHub repository does not match the registered project");
        }
        if (!project.github().requiredCheckName().equals(delivery.checkRunName())) {
            return ApplyResult.rejected("GitHub check run is not the configured delivery check");
        }
        if (!"completed".equals(delivery.action()) || !"completed".equals(delivery.status())) {
            return ApplyResult.applied();
        }
        if (delivery.conclusion() == null) {
            return ApplyResult.rejected("A completed GitHub check run must have a conclusion");
        }
        DeliveryEvidence match = verifiedEvidence(event, delivery.repository(), delivery.pullRequestNo(), delivery.headSha());
        if (match == null) {
            return ApplyResult.deferred("Verified PR evidence is not available for this check run");
        }
        Feedback feedback = currentFeedback(match);
        if (feedback == null) {
            return ApplyResult.rejected("GitHub check run belongs to an expired Feedback cycle");
        }
        boolean succeeded = "success".equals(delivery.conclusion());
        if (feedback.getState() == FeedbackState.BUILD_FAILED && !succeeded) {
            return ApplyResult.applied();
        }
        if (feedback.getState() == FeedbackState.PR_READY) {
            return ApplyResult.deferred("GitHub check run arrived before the authorized merge");
        }
        if (feedback.getState() != FeedbackState.BUILD_RUNNING
                && !(feedback.getState() == FeedbackState.BUILD_FAILED && succeeded)) {
            return ApplyResult.rejected("GitHub check run is not current for this Feedback state");
        }
        workflow.recordBuildEvidence(match.getFeedbackId(), match.getCycleId(), match.getAgentRunId(), succeeded,
                "github:" + event.getExternalEventId());
        return ApplyResult.applied();
    }

    private ApplyResult applyDeploymentStatus(IntegrationEvent event) {
        DeploymentStatusDelivery delivery;
        try {
            delivery = parseDeploymentStatus(event.getPayloadJson());
        } catch (IllegalArgumentException invalid) {
            return ApplyResult.rejected("GitHub deployment status fact is invalid");
        }
        ResolvedProject project = registeredProject(event, delivery.repository());
        if (project == null) {
            return ApplyResult.rejected("GitHub repository does not match the registered project");
        }
        if (!project.github().testEnvironment().equals(delivery.environment())) {
            return ApplyResult.rejected("GitHub deployment is not for the configured test environment");
        }
        boolean succeeded = "success".equals(delivery.state());
        boolean failed = "failure".equals(delivery.state()) || "error".equals(delivery.state());
        if (!succeeded && !failed) {
            return ApplyResult.applied();
        }
        DeliveryEvidence match = verifiedEvidence(event, delivery.repository(), delivery.pullRequestNo(), delivery.headSha());
        if (match == null) {
            return ApplyResult.deferred("Verified PR evidence is not available for this deployment status");
        }
        Feedback feedback = currentFeedback(match);
        if (feedback == null) {
            return ApplyResult.rejected("GitHub deployment belongs to an expired Feedback cycle");
        }
        if (feedback.getState() == FeedbackState.DEPLOY_FAILED && !succeeded) {
            return ApplyResult.applied();
        }
        if (feedback.getState() == FeedbackState.PR_READY || feedback.getState() == FeedbackState.BUILD_RUNNING
                || feedback.getState() == FeedbackState.BUILD_FAILED) {
            return ApplyResult.deferred("GitHub deployment arrived before a successful delivery build");
        }
        if (feedback.getState() != FeedbackState.DEPLOY_RUNNING
                && !(feedback.getState() == FeedbackState.DEPLOY_FAILED && succeeded)) {
            return ApplyResult.rejected("GitHub deployment is not current for this Feedback state");
        }
        workflow.recordDeploymentEvidence(match.getFeedbackId(), match.getCycleId(), match.getAgentRunId(), succeeded,
                "github:" + event.getExternalEventId());
        return ApplyResult.applied();
    }

    private ResolvedProject registeredProject(IntegrationEvent event, String repository) {
        ResolvedProject project = catalog.resolveGitHubRepository(repository).orElse(null);
        return project != null && project.id().equals(event.getProjectId()) ? project : null;
    }

    private DeliveryEvidence verifiedEvidence(IntegrationEvent event, String repository, long pullRequestNo, String headSha) {
        List<DeliveryEvidence> candidates = evidence.findByRepositoryAndPullRequestNoAndState(repository, pullRequestNo,
                DeliveryEvidenceState.VERIFIED);
        return candidates.stream().filter(item -> item.getFeedbackId().equals(event.getFeedbackId())
                || event.getFeedbackId() == null).filter(item -> item.getCycleId().equals(event.getCycleId())
                || event.getCycleId() == null).filter(item -> item.getExpectedHeadSha().equals(headSha)).findFirst().orElse(null);
    }

    private Feedback currentFeedback(DeliveryEvidence expected) {
        Feedback feedback = feedbacks.findById(expected.getFeedbackId()).orElse(null);
        if (feedback == null || !feedback.getCurrentCycleId().equals(expected.getCycleId())) {
            return null;
        }
        return feedback;
    }

    private PullRequestDelivery parsePullRequest(String payloadJson) {
        JsonNode root = parseObject(payloadJson, PULL_REQUEST_FIELDS);
        return new PullRequestDelivery(text(root, "repository"), text(root, "action"), positiveNumber(root, "number"),
                text(root, "baseBranch"), text(root, "headBranch"), text(root, "headSha"), bool(root, "draft"),
                bool(root, "merged"), nullableText(root, "mergedBy"));
    }

    private CheckRunDelivery parseCheckRun(String payloadJson) {
        JsonNode root = parseObject(payloadJson, CHECK_RUN_FIELDS);
        return new CheckRunDelivery(text(root, "repository"), text(root, "action"), positiveNumber(root, "checkRunId"),
                text(root, "checkRunName"), positiveNumber(root, "pullRequestNo"), text(root, "headSha"),
                text(root, "status"), nullableText(root, "conclusion"));
    }

    private DeploymentStatusDelivery parseDeploymentStatus(String payloadJson) {
        JsonNode root = parseObject(payloadJson, DEPLOYMENT_STATUS_FIELDS);
        return new DeploymentStatusDelivery(text(root, "repository"), text(root, "action"),
                positiveNumber(root, "deploymentId"), positiveNumber(root, "deploymentStatusId"),
                positiveNumber(root, "pullRequestNo"), text(root, "headSha"), text(root, "environment"), text(root, "state"));
    }

    private JsonNode parseObject(String payloadJson, Set<String> expectedFields) {
        try {
            JsonNode root = json.readTree(payloadJson);
            if (root == null || !root.isObject()) throw new IllegalArgumentException("payload must be an object");
            Set<String> fields = new java.util.HashSet<>();
            fields.addAll(root.propertyNames());
            if (!expectedFields.equals(fields)) throw new IllegalArgumentException("unexpected fields");
            String digest = text(root, "payloadSha256");
            if (!digest.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("payloadSha256");
            return root;
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

    private record CheckRunDelivery(String repository, String action, long checkRunId, String checkRunName,
            long pullRequestNo, String headSha, String status, String conclusion) {
    }

    private record DeploymentStatusDelivery(String repository, String action, long deploymentId, long deploymentStatusId,
            long pullRequestNo, String headSha, String environment, String state) {
    }
}
