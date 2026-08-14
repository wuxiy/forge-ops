package com.company.forgeops.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 审计（§19.3）：谁提交、平台补了什么、哪个 Agent 执行、PR、Review、CI、验证。 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(Long feedbackId, String actor, String action, String detail) {
        try {
            AuditLog entry = new AuditLog();
            entry.setFeedbackId(feedbackId);
            entry.setActor(actor == null ? "system" : actor);
            entry.setAction(action);
            entry.setDetail(com.company.forgeops.context.builder.ContextPackJson.toJson(
                    java.util.Map.of("detail", detail == null ? "" : detail)));
            repository.save(entry);
            log.info("AUDIT feedback={} actor={} action={} detail={}", feedbackId, actor, action, detail);
        } catch (Exception e) {
            log.error("审计写入失败 feedbackId={}", feedbackId, e);
        }
    }
}
