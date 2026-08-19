package com.company.forgeops.cicd.callback;

import com.company.forgeops.audit.AuditService;
import com.company.forgeops.feedback.application.FeedbackService;
import com.company.forgeops.feedback.domain.Feedback;
import com.company.forgeops.feedback.domain.FeedbackRepository;
import com.company.forgeops.feedback.domain.FeedbackStatus;
import com.company.forgeops.feedback.domain.IntegrationEvent;
import com.company.forgeops.feedback.domain.IntegrationEventRepository;
import com.company.forgeops.multica.client.MulticaClient;
import com.company.forgeops.policy.agent.HumanGate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Git / CI / Deployment 回调（§16）：完全复用现有 CI/CD，Gateway 只收回调并推进状态机。
 * 幂等：forgeops_integration_event (source, external_event_id) 唯一约束。
 */
@Service
public class CallbackService {

    private static final Logger log = LoggerFactory.getLogger(CallbackService.class);

    private final IntegrationEventRepository eventRepository;
    private final FeedbackRepository feedbackRepository;
    private final FeedbackService feedbackService;
    private final MulticaClient multicaClient;
    private final HumanGate humanGate;
    private final AuditService audit;
    private final com.company.forgeops.project.registry.ProjectRegistryService projectRegistryService;

    public CallbackService(
            IntegrationEventRepository eventRepository,
            FeedbackRepository feedbackRepository,
            FeedbackService feedbackService,
            MulticaClient multicaClient,
            HumanGate humanGate,
            AuditService audit,
            com.company.forgeops.project.registry.ProjectRegistryService projectRegistryService) {
        this.eventRepository = eventRepository;
        this.feedbackRepository = feedbackRepository;
        this.feedbackService = feedbackService;
        this.multicaClient = multicaClient;
        this.humanGate = humanGate;
        this.audit = audit;
        this.projectRegistryService = projectRegistryService;
    }

    public record CallbackPayload(
            String projectId,
            String feedbackId,
            String externalEventId,
            String actor,
            String actorType,
            String eventType,
            String prUrl,
            String commitSha,
            String pipelineId,
            String environment,
            String version,
            String status) {
    }

    @Transactional
    public String handle(String source, CallbackPayload payload) {
        // 幂等：同 source + externalEventId 只处理一次
        String externalId = payload.externalEventId() == null || payload.externalEventId().isBlank()
                ? source + "-" + System.currentTimeMillis()
                : payload.externalEventId();
        Optional<IntegrationEvent> existing = eventRepository.findBySourceAndExternalEventId(source, externalId);
        if (existing.isPresent()) {
            log.info("重复回调忽略 source={} externalEventId={}", source, externalId);
            return "DUPLICATED";
        }

        Feedback feedback = resolveFeedback(payload);

        if ("GIT".equals(source)) {
            handleGit(feedback, payload);
        } else if ("CI".equals(source)) {
            handleCi(feedback, payload);
        } else if ("DEPLOYMENT".equals(source)) {
            handleDeployment(feedback, payload);
        } else {
            throw new IllegalArgumentException("未知回调来源: " + source);
        }

        IntegrationEvent event = new IntegrationEvent();
        event.setFeedbackId(feedback == null ? null : feedback.getId());
        event.setSource(source);
        event.setExternalEventId(externalId);
        event.setEventType(payload.eventType() == null ? "UNKNOWN" : payload.eventType());
        event.setPayload(com.company.forgeops.context.builder.ContextPackJson.toJson(payload));
        event.setStatus("PROCESSED");
        eventRepository.save(event);
        return "PROCESSED";
    }

    private void handleGit(Feedback feedback, CallbackPayload payload) {
        // Human Gate：Agent 不允许 Merge（先于一切处理）
        humanGate.assertMergeByHuman(payload.actorType());
        if (feedback == null) return;
        if ("MERGED".equalsIgnoreCase(payload.status()) && feedback.getStatus() == FeedbackStatus.PR_REVIEW) {
            feedbackService.transition(feedback, FeedbackStatus.BUILDING,
                    "PR 已由 " + (payload.actor() == null ? "developer" : payload.actor()) + " 合并");
            audit.record(feedback.getId(), payload.actor(), "PR_MERGED", payload.commitSha());
        }
    }

    private void handleCi(Feedback feedback, CallbackPayload payload) {
        if (feedback == null) return;
        if (feedback.getStatus() != FeedbackStatus.BUILDING) {
            log.info("CI 回调到达但状态为 {}，忽略（feedbackId={}）", feedback.getStatus(), feedback.identifier());
            return;
        }
        if (payload.pipelineId() != null) {
            feedback.setCiPipelineId(payload.pipelineId());
        }
        if ("SUCCESS".equalsIgnoreCase(payload.status())) {
            feedbackService.transition(feedback, FeedbackStatus.DEPLOYING, "CI 构建成功");
        } else {
            feedbackService.transition(feedback, FeedbackStatus.BUILD_FAILED, "CI 构建失败: " + payload.status());
        }
        audit.record(feedback.getId(), "ci:" + payload.pipelineId(), "CI_CALLBACK", payload.status());
    }

    private void handleDeployment(Feedback feedback, CallbackPayload payload) {
        if (feedback == null) return;
        if (feedback.getStatus() != FeedbackStatus.DEPLOYING) {
            log.info("部署回调到达但状态为 {}，忽略（feedbackId={}）", feedback.getStatus(), feedback.identifier());
            return;
        }
        if (!"SUCCESS".equalsIgnoreCase(payload.status())) {
            feedbackService.transition(feedback, FeedbackStatus.BUILD_FAILED, "部署失败: " + payload.status());
            return;
        }
        feedback.setDeploymentVersion(payload.version());
        feedbackService.transition(feedback, FeedbackStatus.WAITING_VERIFY,
                "已发布测试环境 " + payload.environment() + "，版本 " + payload.version());

        // §17 自动通知原反馈人
        feedbackService.addSystemComment(feedback, feedback.identifier() + " 已修复并部署到测试环境（"
                + payload.environment() + "）。版本：" + payload.version() + "。请进行验证。");
        if (feedback.getMulticaIssueId() != null) {
            String ws = multicaClient.resolveWorkspaceId(
                    projectRegistryService.find(feedback.getProjectId())
                            .map(p -> p.multica() == null ? null : p.multica().workspace())
                            .orElse(null));
            multicaClient.addComment(ws, feedback.getMulticaIssueId(),
                    "已发布测试环境（" + payload.environment() + "），版本 " + payload.version()
                            + "。原反馈人 " + feedback.getReporterName() + " 请验证。");
        }
        audit.record(feedback.getId(), "deployment", "DEPLOYED",
                "env=" + payload.environment() + " version=" + payload.version() + " commit=" + payload.commitSha());
    }

    private Feedback resolveFeedback(CallbackPayload payload) {
        String feedbackId = payload.feedbackId();
        if (feedbackId == null || feedbackId.isBlank()) {
            return null;
        }
        var parsed = com.company.forgeops.feedback.domain.FeedbackIdentifier.parse(feedbackId);
        if (parsed == null) {
            throw new IllegalArgumentException("feedbackId 非法（期望 ADB-FB-1001 或 FB-1002）: " + feedbackId);
        }
        // 带前缀标识精确定位；旧格式 FB-n 兼容：先按 display_no 精确匹配，回退主键 id（历史数据回填前）
        return feedbackRepository.findByFeedbackPrefixAndDisplayNo(parsed.prefix(), parsed.displayNo())
                .or(() -> parsed.prefix() == null
                        ? feedbackRepository.findByFeedbackPrefixIsNullAndDisplayNo(parsed.displayNo())
                        : java.util.Optional.<Feedback>empty())
                .or(() -> feedbackRepository.findById(parsed.displayNo()))
                .orElseThrow(() -> new IllegalArgumentException("反馈不存在: " + feedbackId));
    }
}
