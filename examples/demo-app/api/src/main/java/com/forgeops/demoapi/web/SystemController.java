package com.forgeops.demoapi.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SystemController {

    private final VersionProvider versionProvider;
    private final RequestLogStore requestLogStore;

    public SystemController(VersionProvider versionProvider, RequestLogStore requestLogStore) {
        this.versionProvider = versionProvider;
        this.requestLogStore = requestLogStore;
    }

    /** SDK / Gateway 读取后端版本与 Commit。 */
    @GetMapping("/version")
    public Map<String, Object> version() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "demo-api");
        body.put("version", versionProvider.version());
        body.put("commit", versionProvider.commit());
        return body;
    }

    /** 按 requestId 查结构化日志摘录（ForgeOps Gateway Context Pack 日志补齐用）。 */
    @GetMapping("/admin/requests/{requestId}")
    public Map<String, Object> requestLogs(@PathVariable String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("entries", requestLogStore.findByRequestId(requestId));
        return body;
    }
}
