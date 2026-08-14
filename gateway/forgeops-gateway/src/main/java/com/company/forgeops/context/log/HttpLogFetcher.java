package com.company.forgeops.context.log;

import com.company.forgeops.project.registry.ProjectConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 日志摘录拉取（§11.2 / §8）：按 requestId 查询业务服务日志。
 * V0.1 demo 适配 http-request-log 类型（demo-api 内存日志端点）；
 * 后续接入 elasticsearch 时新增 adapter 即可，Context Pack 结构不变。
 */
@Component
public class HttpLogFetcher {

    private static final Logger log = LoggerFactory.getLogger(HttpLogFetcher.class);

    public record LogExcerpt(String source, String requestId, List<String> entries) {
    }

    /** 支持的 observability.logs.type。 */
    public boolean supports(ProjectConfig config) {
        String type = config.observability() == null || config.observability().logs() == null
                ? null
                : config.observability().logs().type();
        return "http-request-log".equals(type);
    }

    @SuppressWarnings("unchecked")
    public LogExcerpt fetch(ProjectConfig config, String requestId) {
        if (!supports(config) || requestId == null || requestId.isBlank()) {
            return null;
        }
        String baseUrl = config.observability().logs().baseUrl();
        String service = config.observability().logs().service();
        try {
            Map<String, Object> response = RestClient.create().get()
                    .uri(baseUrl + "/api/admin/requests/{requestId}", requestId)
                    .retrieve()
                    .body(Map.class);
            if (response == null) return null;
            Object entries = response.get("entries");
            if (!(entries instanceof List<?> list) || list.isEmpty()) return null;

                    List<String> lines = new ArrayList<>();
                    for (Object entry : list) {
                        if (entry instanceof Map<?, ?> m) {
                            List<String> parts = new ArrayList<>();
                            for (Map.Entry<?, ?> e : m.entrySet()) {
                                parts.add(e.getKey() + "=" + e.getValue());
                            }
                            lines.add("{" + String.join(", ", parts) + "}");
                        }
                    }
                    return new LogExcerpt(service, requestId, lines);
        } catch (Exception e) {
            log.warn("拉取日志摘录失败 service={} requestId={}: {}", service, requestId, e.getMessage());
            return null;
        }
    }
}
