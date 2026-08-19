package com.company.forgeops.feedback.application;

import com.company.forgeops.audit.AuditService;
import com.company.forgeops.feedback.domain.Feedback;
import com.company.forgeops.feedback.domain.FeedbackRepository;
import com.company.forgeops.feedback.domain.FeedbackStatus;
import com.company.forgeops.multica.client.MulticaClient;
import com.company.forgeops.project.registry.ProjectConfig;
import com.company.forgeops.project.registry.ProjectRegistryService;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Multica 轮询器（§6.1 状态机驱动）：
 * 读取 Issue 评论中的结构化结果标记（由 Triage/Coding Skill 输出），
 * 推进 SUBMITTED->TRIAGING->CODING->PR_REVIEW，并把 Agent 分析同步到反馈时间线。
 */
@Component
public class MulticaPoller {

    public static final String MARKER_TRIAGE = "TRIAGE_RESULT:";
    public static final String MARKER_CODING = "CODING_RESULT:";
    public static final Pattern PR_URL_PATTERN = Pattern.compile("PR_URL:\\s*(https?://\\S+)");

    private static final Logger log = LoggerFactory.getLogger(MulticaPoller.class);

    private final FeedbackRepository feedbackRepository;
    private final FeedbackService feedbackService;
    private final MulticaClient multicaClient;
    private final ProjectRegistryService registry;
    private final AuditService audit;
    private final boolean enabled;

    public MulticaPoller(
            FeedbackRepository feedbackRepository,
            FeedbackService feedbackService,
            MulticaClient multicaClient,
            ProjectRegistryService registry,
            AuditService audit,
            @Value("${forgeops.poller.enabled}") boolean enabled) {
        this.feedbackRepository = feedbackRepository;
        this.feedbackService = feedbackService;
        this.multicaClient = multicaClient;
        this.registry = registry;
        this.audit = audit;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${forgeops.poller.fixed-delay-ms:10000}")
    public void poll() {
        if (!enabled) return;
        List<Feedback> active = feedbackRepository.findByStatusIn(List.copyOf(FeedbackStatus.agentActive()));
        for (Feedback feedback : active) {
            if (feedback.getMulticaIssueId() == null) continue;
            try {
                process(feedback);
            } catch (Exception e) {
                log.warn("轮询反馈 {} 失败: {}", feedback.identifier(), e.getMessage());
            }
        }
    }

    @Transactional
    protected void process(Feedback feedback) {
        ProjectConfig project = registry.find(feedback.getProjectId()).orElse(null);
        String workspaceId = multicaClient.resolveWorkspaceId(
                project == null || project.multica() == null ? null : project.multica().workspace());
        List<MulticaClient.Comment> comments = multicaClient.listComments(workspaceId, feedback.getMulticaIssueId());
        Optional<MulticaClient.Comment> latestTriage = lastMarker(comments, MARKER_TRIAGE);
        Optional<MulticaClient.Comment> latestCoding = lastMarker(comments, MARKER_CODING);
        log.info("轮询 {} status={} workspace={} comments={} triageMarker={} codingMarker={}",
                feedback.identifier(), feedback.getStatus(), workspaceId, comments.size(),
                latestTriage.isPresent(), latestCoding.isPresent());

        if (feedback.getStatus() == FeedbackStatus.TRIAGING && latestTriage.isPresent()) {
            handleTriageResult(feedback, project, workspaceId, latestTriage.get());
        } else if (feedback.getStatus() == FeedbackStatus.CODING && latestCoding.isPresent()) {
            handleCodingResult(feedback, workspaceId, latestCoding.get());
        }
    }

    private void handleTriageResult(Feedback feedback, ProjectConfig project, String workspaceId, MulticaClient.Comment comment) {
        String content = comment.content();
        if (project == null) return;

        if (content.contains(MARKER_TRIAGE + " PROCEED_CODING")) {
            if (project.policyFlag("autoCode", true)) {
                feedbackService.transition(feedback, FeedbackStatus.CODING, "Triage 判定可自动修复");
                String codingAgentId = project.multica() == null ? null
                        : multicaClient.findAgentIdByName(workspaceId, project.multica().codingAgent());
                multicaClient.updateIssue(workspaceId, feedback.getMulticaIssueId(), "todo", codingAgentId);
                multicaClient.addComment(workspaceId, feedback.getMulticaIssueId(),
                        "Triage 完成，已转交 Coding Agent（" + project.multica().codingAgent() + "）。请按 Coding Skill 处理。");
                audit.record(feedback.getId(), comment.authorId(), "TRIAGE_DONE", "proceed to coding");
            }
        } else if (content.contains(MARKER_TRIAGE + " NEED_INFO")) {
            feedbackService.transition(feedback, FeedbackStatus.NEED_INFO, "Triage 判定上下文不足");
        } else {
            log.info("反馈 {} Triage 结果未识别，忽略", feedback.identifier());
        }
    }

    private void handleCodingResult(Feedback feedback, String workspaceId, MulticaClient.Comment comment) {
        String content = comment.content();
        if (content.contains(MARKER_CODING + " PR_CREATED")) {
            Matcher matcher = PR_URL_PATTERN.matcher(content);
            if (matcher.find()) {
                feedback.setPrUrl(matcher.group(1));
                feedbackService.transition(feedback, FeedbackStatus.PR_REVIEW, "Draft PR 已创建，等待人工 Review");
                multicaClient.addComment(workspaceId, feedback.getMulticaIssueId(),
                        "Draft PR 已创建：" + matcher.group(1) + " 。等待开发人员人工 Review（Human Gate：禁止 Agent 合并）。");
                audit.record(feedback.getId(), comment.authorId(), "DRAFT_PR_CREATED", matcher.group(1));
            }
        } else if (content.contains(MARKER_CODING + " PR_PENDING_MANUAL")) {
            Matcher matcher = PR_URL_PATTERN.matcher(content);
            if (matcher.find()) {
                feedback.setPrUrl(matcher.group(1));
                feedbackService.transition(feedback, FeedbackStatus.PR_REVIEW, "分支已推送，PR 待人工创建");
                audit.record(feedback.getId(), comment.authorId(), "BRANCH_PUSHED", matcher.group(1));
            }
        } else if (content.contains(MARKER_CODING + " FAILED")) {
            feedbackService.transition(feedback, FeedbackStatus.AGENT_FAILED, "Coding Agent 失败");
        } else if (content.contains(MARKER_CODING + " NEED_INFO")) {
            feedbackService.transition(feedback, FeedbackStatus.NEED_INFO, "Coding Agent 需要补充信息");
        }
    }

    private Optional<MulticaClient.Comment> lastMarker(List<MulticaClient.Comment> comments, String marker) {
        MulticaClient.Comment found = null;
        for (MulticaClient.Comment c : comments) {
            if (c.content() != null && c.content().contains(marker)) {
                found = c;
            }
        }
        return Optional.ofNullable(found);
    }
}
