package com.forgeops.spring.boot.tracing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * ForgeOps 追踪端点：
 * - GET /api/version                      供 SDK / Gateway 读取 version + commit
 * - GET /api/admin/requests/{requestId}   供 Context Pack 按 requestId 拉日志摘录
 * 返回 Map 由宿主应用的 HTTP 消息转换器序列化（不绑定 Jackson 版本）。
 */
@RestController
public class TracingEndpoints {

    private final AppVersionInfo versionInfo;
    private final RequestLogStore requestLogStore;
    private final ForgeOpsTracingProperties properties;

    public TracingEndpoints(AppVersionInfo versionInfo, RequestLogStore requestLogStore,
                            ForgeOpsTracingProperties properties) {
        this.versionInfo = versionInfo;
        this.requestLogStore = requestLogStore;
        this.properties = properties;
    }

    @GetMapping("/api/version")
    public Map<String, Object> version() {
        if (!properties.isVersionEndpoint()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", versionInfo.serviceName());
        body.put("version", versionInfo.version());
        body.put("commit", versionInfo.commit());
        return body;
    }

    @GetMapping("/api/admin/requests/{requestId}")
    public Map<String, Object> requestLogs(@PathVariable String requestId) {
        if (!properties.isExposeRequestLogs()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        List<Map<String, Object>> entries = requestLogStore.findByRequestId(requestId);
        body.put("entries", entries);
        body.put("count", entries.size());
        return body;
    }
}
