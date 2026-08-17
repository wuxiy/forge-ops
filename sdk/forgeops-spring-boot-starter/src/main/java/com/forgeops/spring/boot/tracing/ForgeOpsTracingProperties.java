package com.forgeops.spring.boot.tracing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ForgeOps 接入配置（业务应用 application.yml）：
 *
 * forgeops:
 *   tracing:
 *     enabled: true                  # 引入依赖即默认开启
 *     version-endpoint: true         # 暴露 GET /api/version（version/commit）
 *     expose-request-logs: true      # 暴露 GET /api/admin/requests/{requestId}（供 Context Pack 拉日志）
 *     tracked-requests: 500          # 内存请求日志缓冲条数
 */
@ConfigurationProperties(prefix = "forgeops.tracing")
public class ForgeOpsTracingProperties {

    /** 总开关：false 时整个链路（Filter/MDC/端点）不装配。 */
    private boolean enabled = true;

    /** 暴露 GET /api/version。 */
    private boolean versionEndpoint = true;

    /** 暴露 GET /api/admin/requests/{requestId}。 */
    private boolean exposeRequestLogs = true;

    /** 内存请求日志缓冲的 requestId 数量上限。 */
    private int trackedRequests = 500;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isVersionEndpoint() { return versionEndpoint; }
    public void setVersionEndpoint(boolean versionEndpoint) { this.versionEndpoint = versionEndpoint; }

    public boolean isExposeRequestLogs() { return exposeRequestLogs; }
    public void setExposeRequestLogs(boolean exposeRequestLogs) { this.exposeRequestLogs = exposeRequestLogs; }

    public int getTrackedRequests() { return trackedRequests; }
    public void setTrackedRequests(int trackedRequests) { this.trackedRequests = trackedRequests; }
}
