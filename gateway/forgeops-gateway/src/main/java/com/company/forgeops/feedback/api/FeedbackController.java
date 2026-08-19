package com.company.forgeops.feedback.api;

import com.company.forgeops.feedback.api.dto.FeedbackDetailDto;
import com.company.forgeops.feedback.api.dto.FeedbackListItemDto;
import com.company.forgeops.feedback.api.dto.FeedbackSubmissionDto;
import com.company.forgeops.feedback.application.FeedbackService;
import com.company.forgeops.feedback.domain.Feedback;
import com.company.forgeops.feedback.domain.FeedbackComment;
import com.company.forgeops.feedback.domain.FeedbackCommentRepository;
import com.company.forgeops.verification.VerificationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 反馈 API（§9.3）。 */
@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final FeedbackCommentRepository commentRepository;
    private final VerificationService verificationService;
    private final com.company.forgeops.feedback.domain.FeedbackRepository feedbackRepository;

    public FeedbackController(
            FeedbackService feedbackService,
            FeedbackCommentRepository commentRepository,
            VerificationService verificationService,
            com.company.forgeops.feedback.domain.FeedbackRepository feedbackRepository) {
        this.feedbackService = feedbackService;
        this.commentRepository = commentRepository;
        this.verificationService = verificationService;
        this.feedbackRepository = feedbackRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> submit(@Valid @RequestBody FeedbackSubmissionDto submission) {
        Feedback feedback = feedbackService.intake(submission);
        return Map.of("id", feedback.identifier(), "status", feedback.getStatus().name());
    }

    @GetMapping
    public List<FeedbackListItemDto> listMine(
            @RequestParam String reporter,
            @RequestParam(required = false) String projectId) {
        List<Feedback> feedbacks = feedbackService.listByReporter(reporter, projectId);
        return feedbacks.stream().map(FeedbackListItemDto::from).toList();
    }

    @GetMapping("/{id}")
    public FeedbackDetailDto detail(@PathVariable String id) {
        Feedback feedback = resolveFeedback(id);
        List<FeedbackComment> comments = commentRepository.findByFeedbackIdOrderByCreatedAtAsc(feedback.getId());
        return FeedbackDetailDto.from(feedback, comments);
    }

    @PostMapping("/{id}/comment")
    public Map<String, String> comment(@PathVariable String id, @RequestBody Map<String, String> body) {
        Feedback feedback = resolveFeedback(id);
        FeedbackComment comment = new FeedbackComment();
        comment.setFeedbackId(feedback.getId());
        comment.setAuthorType("USER");
        comment.setAuthorId(body.getOrDefault("author", "user"));
        comment.setContent(body.getOrDefault("content", ""));
        commentRepository.save(comment);
        return Map.of("status", "OK");
    }

    @PostMapping("/{id}/verify")
    public Map<String, String> verify(@PathVariable String id, @RequestBody Map<String, String> body) {
        Feedback feedback = verificationService.verifyPass(
                resolveFeedback(id).getId(),
                body.getOrDefault("verifierName", "unknown"),
                body.get("verifierId"),
                body.get("comment"));
        return Map.of("id", feedback.identifier(), "status", feedback.getStatus().name());
    }

    @PostMapping("/{id}/reopen")
    public Map<String, String> reopen(@PathVariable String id, @RequestBody ReopenRequest body) {
        Feedback feedback = verificationService.verifyFail(
                resolveFeedback(id).getId(),
                body.verifierName() == null ? "unknown" : body.verifierName(),
                body.verifierId(),
                body.comment(),
                body.requests(),
                body.consoleErrors());
        return Map.of("id", feedback.identifier(), "status", feedback.getStatus().name());
    }

    public record ReopenRequest(
            String verifierName,
            String verifierId,
            String comment,
            List<FeedbackSubmissionDto.RequestSummary> requests,
            List<String> consoleErrors) {
    }

    /** 按对外标识精确定位（ADB-FB-1001 / 旧 FB-1002）。 */
    Feedback resolveFeedback(String id) {
        var parsed = com.company.forgeops.feedback.domain.FeedbackIdentifier.parse(id);
        if (parsed == null) {
            throw new IllegalArgumentException("反馈 ID 非法（期望 ADB-FB-1001 或 FB-1002）: " + id);
        }
        return feedbackRepository
                .findByFeedbackPrefixAndDisplayNo(parsed.prefix(), parsed.displayNo())
                .or(() -> parsed.prefix() == null
                        ? feedbackRepository.findByFeedbackPrefixIsNullAndDisplayNo(parsed.displayNo())
                        : java.util.Optional.<Feedback>empty())
                .orElseThrow(() -> new IllegalArgumentException("反馈不存在: " + id));
    }
}
