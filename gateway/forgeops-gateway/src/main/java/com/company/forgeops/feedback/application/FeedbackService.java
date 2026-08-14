package com.company.forgeops.feedback.application;

import com.company.forgeops.audit.AuditService;
import com.company.forgeops.context.builder.ContextPackBuilder;
import com.company.forgeops.context.builder.ContextPackJson;
import com.company.forgeops.feedback.api.dto.FeedbackSubmissionDto;
import com.company.forgeops.feedback.domain.ContextSnapshot;
import com.company.forgeops.feedback.domain.ContextSnapshotRepository;
import com.company.forgeops.feedback.domain.Feedback;
import com.company.forgeops.feedback.domain.FeedbackComment;
import com.company.forgeops.feedback.domain.FeedbackCommentRepository;
import com.company.forgeops.feedback.domain.FeedbackRepository;
import com.company.forgeops.feedback.domain.FeedbackStatus;
import com.company.forgeops.multica.client.MulticaClient;
import com.company.forgeops.multica.client.MulticaIssueService;
import com.company.forgeops.policy.security.PiiSanitizer;
import com.company.forgeops.project.registry.ProjectConfig;
import com.company.forgeops.project.registry.ProjectRegistryService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feedback Intake 主流程（§5/§9/§11）：
 * 校验 -> PII 脱敏 -> 落库 -> Context Pack 构建 -> Multica Issue 创建 -> TRIAGING。
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final FeedbackRepository feedbackRepository;
    private final ContextSnapshotRepository contextRepository;
    private final FeedbackCommentRepository commentRepository;
    private final ProjectRegistryService registry;
    private final ContextPackBuilder contextPackBuilder;
    private final MulticaClient multicaClient;
    private final MulticaIssueService multicaIssueService;
    private final PiiSanitizer sanitizer;
    private final AuditService audit;
    private final Path screenshotDir;

    public FeedbackService(
            FeedbackRepository feedbackRepository,
            ContextSnapshotRepository contextRepository,
            FeedbackCommentRepository commentRepository,
            ProjectRegistryService registry,
            ContextPackBuilder contextPackBuilder,
            MulticaClient multicaClient,
            MulticaIssueService multicaIssueService,
            PiiSanitizer sanitizer,
            AuditService audit,
            @Value("${forgeops.storage.screenshot-dir}") String screenshotDir) {
        this.feedbackRepository = feedbackRepository;
        this.contextRepository = contextRepository;
        this.commentRepository = commentRepository;
        this.registry = registry;
        this.contextPackBuilder = contextPackBuilder;
        this.multicaClient = multicaClient;
        this.multicaIssueService = multicaIssueService;
        this.sanitizer = sanitizer;
        this.audit = audit;
        this.screenshotDir = Path.of(screenshotDir);
    }

    @Transactional
    public Feedback intake(FeedbackSubmissionDto submission) {
        ProjectConfig project = registry.find(submission.projectId())
                .orElseThrow(() -> new IllegalArgumentException("未知项目: " + submission.projectId() + "（请检查 Project Registry）"));
        if (project.feedback() == null || !Boolean.TRUE.equals(project.feedback().get("enabled"))) {
            throw new IllegalArgumentException("项目 " + submission.projectId() + " 未开启反馈通道");
        }

        // 1. 落库（SUBMITTED）
        Feedback feedback = new Feedback();
        feedback.setProjectId(submission.projectId());
        feedback.setType(submission.type());
        String title = submission.title() != null && !submission.title().isBlank()
                ? submission.title()
                : deriveTitle(submission.description());
        feedback.setTitle(sanitizer.sanitize(title));
        feedback.setDescription(sanitizer.sanitize(submission.description()));
        feedback.setExpectedBehavior(sanitizer.sanitize(submission.expectedBehavior()));
        feedback.setSteps(sanitizer.sanitize(submission.steps()));
        feedback.setNote(sanitizer.sanitize(submission.note()));
        feedback.setReporterId(submission.reporter() == null ? null : submission.reporter().id());
        feedback.setReporterName(submission.reporter() == null ? "匿名" : submission.reporter().name());
        feedback.setEnvironment(submission.environment() == null ? "test" : submission.environment());
        feedback.setPageUrl(submission.page() == null ? null : submission.page().url());
        if (submission.frontend() != null) {
            feedback.setFrontendVersion(submission.frontend().version());
            feedback.setFrontendCommit(submission.frontend().commit());
        }
        if (submission.backend() != null) {
            feedback.setBackendVersion(submission.backend().version());
            feedback.setBackendCommit(submission.backend().commit());
        }
        feedback.setStatus(FeedbackStatus.SUBMITTED);
        feedback = feedbackRepository.save(feedback);

        // 2. 截图落盘
        String screenshotPath = storeScreenshot(feedback, submission.screenshot());

        // 3. Context Pack（CONTEXT_BUILDING -> TRIAGING）
        feedback.setStatus(FeedbackStatus.CONTEXT_BUILDING);
        Map<String, Object> contextPack = contextPackBuilder.build(feedback, submission, project, screenshotPath);
        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setFeedbackId(feedback.getId());
        snapshot.setSchemaVersion("1.0");
        snapshot.setReason("INITIAL");
        snapshot.setContextJson(ContextPackJson.toJson(contextPack));
        contextRepository.save(snapshot);

        // 4. Multica Issue（策略允许时自动创建并指派 Triage Agent）
        if (project.policyFlag("autoAnalyze", true)) {
            createMulticaIssue(feedback, project, contextPack);
        } else {
            feedback.setStatus(FeedbackStatus.TRIAGING);
        }

        audit.record(feedback.getId(), feedback.getReporterName(), "FEEDBACK_SUBMITTED",
                "type=" + feedback.getType() + ", contextPackV1 已生成, multicaIssue="
                        + feedback.getMulticaIssueId());
        return feedback;
    }

    private void createMulticaIssue(Feedback feedback, ProjectConfig project, Map<String, Object> contextPack) {
        String issueTitle = "[" + feedback.getType() + "] " + feedback.getTitle() + " (" + feedback.identifier() + ")";
        String body = multicaIssueService.renderIssueBody(feedback, contextPack, project);
        String agentId = null;
        if (project.multica() != null && project.multica().triageAgent() != null) {
            agentId = multicaClient.findAgentIdByName(project.multica().triageAgent());
            if (agentId == null) {
                log.warn("未找到 Triage Agent {}，Issue 将无人指派", project.multica().triageAgent());
            }
        }
        MulticaClient.Issue issue = multicaClient.createIssue(issueTitle, body, agentId);
        feedback.setMulticaIssueId(issue.id());
        feedback.setMulticaIssueUrl(multicaClient.issueUrl(issue.id()));
        feedback.setStatus(FeedbackStatus.TRIAGING);

        addSystemComment(feedback, "已创建 Multica Issue " + issue.identifier() + " 并指派 Triage Agent 分析。");
        audit.record(feedback.getId(), "system", "MULTICA_ISSUE_CREATED",
                "issueId=" + issue.id() + " identifier=" + issue.identifier() + " triageAgentId=" + agentId);
    }

    private String storeScreenshot(Feedback feedback, String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:image/png;base64,")) {
            return null;
        }
        try {
            Files.createDirectories(screenshotDir);
            byte[] bytes = Base64.getDecoder().decode(dataUrl.substring("data:image/png;base64,".length()));
            Path target = screenshotDir.resolve(feedback.identifier() + "-" + System.currentTimeMillis() + ".png");
            Files.copy(new java.io.ByteArrayInputStream(bytes), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        } catch (IOException | IllegalArgumentException e) {
            log.warn("截图保存失败 feedbackId={}: {}", feedback.getId(), e.getMessage());
            return null;
        }
    }

    private String deriveTitle(String description) {
        String firstLine = description == null ? "" : description.lines().filter(s -> !s.isBlank()).findFirst().orElse("");
        return firstLine.length() > 60 ? firstLine.substring(0, 60) : firstLine;
    }

    @Transactional
    public void addSystemComment(Feedback feedback, String content) {
        FeedbackComment comment = new FeedbackComment();
        comment.setFeedbackId(feedback.getId());
        comment.setAuthorType("SYSTEM");
        comment.setAuthorId("forgeops-gateway");
        comment.setContent(content);
        commentRepository.save(comment);
    }

    @Transactional
    public void transition(Feedback feedback, FeedbackStatus target, String reason) {
        feedback.transitionTo(target);
        feedbackRepository.save(feedback);
        addSystemComment(feedback, "状态变更: " + target + (reason == null ? "" : "（" + reason + "）"));
        audit.record(feedback.getId(), "system", "STATUS_TRANSITION", target + " " + (reason == null ? "" : reason));
    }

    public Feedback getById(Long id) {
        return feedbackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("反馈不存在: FB-" + id));
    }

    public List<Feedback> listByReporter(String reporter, String projectId) {
        return feedbackRepository.findByReporterNameAndProjectIdOrderByCreatedAtDesc(reporter, projectId);
    }
}
