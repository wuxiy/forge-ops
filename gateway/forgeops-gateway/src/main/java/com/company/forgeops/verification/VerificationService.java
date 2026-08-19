package com.company.forgeops.verification;

import com.company.forgeops.audit.AuditService;
import com.company.forgeops.context.builder.ContextPackBuilder;
import com.company.forgeops.context.builder.ContextPackJson;
import com.company.forgeops.feedback.api.dto.FeedbackSubmissionDto;
import com.company.forgeops.feedback.application.FeedbackService;
import com.company.forgeops.feedback.domain.ContextSnapshot;
import com.company.forgeops.feedback.domain.ContextSnapshotRepository;
import com.company.forgeops.feedback.domain.Feedback;
import com.company.forgeops.feedback.domain.FeedbackRepository;
import com.company.forgeops.feedback.domain.FeedbackStatus;
import com.company.forgeops.feedback.domain.VerificationRecord;
import com.company.forgeops.feedback.domain.VerificationRecordRepository;
import com.company.forgeops.multica.client.MulticaClient;
import com.company.forgeops.project.registry.ProjectConfig;
import com.company.forgeops.project.registry.ProjectRegistryService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 验证闭环（§17）：
 * 验证通过 -> DONE + Multica Issue Done；
 * 仍有问题 -> 原 Issue Reopen（严禁新建孤立 Issue）+ 追加 Context 快照 + 重新指派 Agent。
 */
@Service
public class VerificationService {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackService feedbackService;
    private final VerificationRecordRepository verificationRepository;
    private final ContextSnapshotRepository contextRepository;
    private final ContextPackBuilder contextPackBuilder;
    private final MulticaClient multicaClient;
    private final ProjectRegistryService registry;
    private final AuditService audit;

    public VerificationService(
            FeedbackRepository feedbackRepository,
            FeedbackService feedbackService,
            VerificationRecordRepository verificationRepository,
            ContextSnapshotRepository contextRepository,
            ContextPackBuilder contextPackBuilder,
            MulticaClient multicaClient,
            ProjectRegistryService registry,
            AuditService audit) {
        this.feedbackRepository = feedbackRepository;
        this.feedbackService = feedbackService;
        this.verificationRepository = verificationRepository;
        this.contextRepository = contextRepository;
        this.contextPackBuilder = contextPackBuilder;
        this.multicaClient = multicaClient;
        this.registry = registry;
        this.audit = audit;
    }

    @Transactional
    public Feedback verifyPass(Long feedbackId, String verifierName, String verifierId, String comment) {
        Feedback feedback = mustBeWaitingVerify(feedbackId);
        VerificationRecord record = new VerificationRecord();
        record.setFeedbackId(feedback.getId());
        record.setVerifierName(verifierName);
        record.setVerifierId(verifierId);
        record.setResult("PASS");
        record.setComment(comment);
        verificationRepository.save(record);

        feedbackService.transition(feedback, FeedbackStatus.DONE, "原反馈人验证通过");

        if (feedback.getMulticaIssueId() != null) {
            String ws = multicaClient.resolveWorkspaceId(workspaceOf(feedback));
            multicaClient.updateIssue(ws, feedback.getMulticaIssueId(), "done", null);
            multicaClient.addComment(ws, feedback.getMulticaIssueId(),
                    "原反馈人 " + verifierName + " 验证通过，反馈 " + feedback.identifier() + " 关闭。");
        }
        audit.record(feedback.getId(), verifierName, "VERIFY_PASS", comment == null ? "" : comment);
        return feedbackRepository.save(feedback);
    }

    @Transactional
    public Feedback verifyFail(Long feedbackId, String verifierName, String verifierId, String comment,
                               List<FeedbackSubmissionDto.RequestSummary> newRequests, List<String> newConsoleErrors) {
        Feedback feedback = mustBeWaitingVerify(feedbackId);
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("仍有问题时必须填写验证说明");
        }

        VerificationRecord record = new VerificationRecord();
        record.setFeedbackId(feedback.getId());
        record.setVerifierName(verifierName);
        record.setVerifierId(verifierId);
        record.setResult("FAIL");
        record.setComment(comment);
        verificationRepository.save(record);

        // REOPENED -> TRIAGING（回到 Agent 链路，不新建 Issue）
        feedbackService.transition(feedback, FeedbackStatus.REOPENED, "原反馈人验证未通过");
        feedbackService.transition(feedback, FeedbackStatus.TRIAGING, "Reopen 后自动重新进入分析");

        // 追加 Context Pack 快照（append，不覆盖历史）
        appendReopenContext(feedback, comment, newRequests, newConsoleErrors);

        // Multica 原 Issue Reopen + 重新指派 Triage Agent
        ProjectConfig project = registry.find(feedback.getProjectId()).orElse(null);
        if (feedback.getMulticaIssueId() != null) {
            String ws = multicaClient.resolveWorkspaceId(workspaceOf(feedback));
            String triageAgentId = project == null || project.multica() == null ? null
                    : multicaClient.findAgentIdByName(ws, project.multica().triageAgent());
            multicaClient.updateIssue(ws, feedback.getMulticaIssueId(), "todo", triageAgentId);
            multicaClient.addComment(ws, feedback.getMulticaIssueId(),
                    "反馈人 " + verifierName + " 验证未通过，问题 Reopen。\n\n验证说明：\n" + comment
                            + "\n\n请 Triage Agent 基于新增上下文继续分析（含新增失败请求与控制台错误）。");
        }
        audit.record(feedback.getId(), verifierName, "VERIFY_FAIL_REOPEN", comment);
        return feedbackRepository.save(feedback);
    }

    private void appendReopenContext(Feedback feedback, String comment,
                                     List<FeedbackSubmissionDto.RequestSummary> newRequests, List<String> newConsoleErrors) {
        Map<String, Object> pack = contextPackBuilder.build(feedback, null, registry.find(feedback.getProjectId()).orElse(null));
        pack.put("reopen", Map.of(
                "comment", comment,
                "newRequests", newRequests == null ? List.of() : newRequests,
                "newConsoleErrors", newConsoleErrors == null ? List.of() : newConsoleErrors));
        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setFeedbackId(feedback.getId());
        snapshot.setSchemaVersion("1.0");
        snapshot.setReason("REOPEN_APPEND");
        snapshot.setContextJson(ContextPackJson.toJson(pack));
        contextRepository.save(snapshot);
    }

    private String workspaceOf(Feedback feedback) {
        return registry.find(feedback.getProjectId())
                .map(p -> p.multica() == null ? null : p.multica().workspace())
                .orElse(null);
    }

    private Feedback mustBeWaitingVerify(Long feedbackId) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new IllegalArgumentException("反馈不存在: FB-" + feedbackId));
        if (feedback.getStatus() != FeedbackStatus.WAITING_VERIFY) {
            throw new IllegalStateException("当前状态 " + feedback.getStatus().displayStatus()
                    + " 不可验证（仅 待验证 状态允许验证/Reopen）");
        }
        return feedback;
    }
}
