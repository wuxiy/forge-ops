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

    public FeedbackController(
            FeedbackService feedbackService,
            FeedbackCommentRepository commentRepository,
            VerificationService verificationService) {
        this.feedbackService = feedbackService;
        this.commentRepository = commentRepository;
        this.verificationService = verificationService;
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
        Feedback feedback = feedbackService.getById(parseId(id));
        List<FeedbackComment> comments = commentRepository.findByFeedbackIdOrderByCreatedAtAsc(feedback.getId());
        return FeedbackDetailDto.from(feedback, comments);
    }

    @PostMapping("/{id}/comment")
    public Map<String, String> comment(@PathVariable String id, @RequestBody Map<String, String> body) {
        Feedback feedback = feedbackService.getById(parseId(id));
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
                parseId(id),
                body.getOrDefault("verifierName", "unknown"),
                body.get("verifierId"),
                body.get("comment"));
        return Map.of("id", feedback.identifier(), "status", feedback.getStatus().name());
    }

    @PostMapping("/{id}/reopen")
    public Map<String, String> reopen(@PathVariable String id, @RequestBody ReopenRequest body) {
        Feedback feedback = verificationService.verifyFail(
                parseId(id),
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

    static Long parseId(String id) {
        String numeric = id != null && id.startsWith("FB-") ? id.substring(3) : id;
        try {
            return Long.valueOf(numeric);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("反馈 ID 非法: " + id);
        }
    }
}
