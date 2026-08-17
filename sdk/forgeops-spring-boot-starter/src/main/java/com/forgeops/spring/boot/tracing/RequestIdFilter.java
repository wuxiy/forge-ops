package com.forgeops.spring.boot.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * ForgeOps Request-ID 链路（架构文档 §8）：
 * 浏览器/SDK 注入 X-Request-ID（ULID），透传或补生成 -> MDC -> 结构化 JSON 访问日志
 * （requestId/traceId/service/uri/httpMethod/status/durationMs/version/commitSha/exception）
 * -> RequestLogStore 供 Context Pack 按 requestId 拉取。
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-ID";
    public static final String HEADER_TRACE_ID = "X-Trace-ID";

    private static final Logger accessLog = LoggerFactory.getLogger("ACCESS");

    private final RequestLogStore requestLogStore;
    private final AppVersionInfo versionInfo;

    public RequestIdFilter(RequestLogStore requestLogStore, AppVersionInfo versionInfo) {
        this.requestLogStore = requestLogStore;
        this.versionInfo = versionInfo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = Ulid.next();
        }
        String traceId = request.getHeader(HEADER_TRACE_ID);

        MDC.put("requestId", requestId);
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        response.setHeader(HEADER_REQUEST_ID, requestId);

        long start = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = response.getStatus();
            String exception = failure == null ? ""
                    : failure.getClass().getSimpleName() + ": " + failure.getMessage();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ts", Instant.now().toString());
            entry.put("requestId", requestId);
            entry.put("traceId", traceId == null ? "" : traceId);
            entry.put("service", versionInfo.serviceName());
            entry.put("uri", uri);
            entry.put("httpMethod", method);
            entry.put("status", status);
            entry.put("durationMs", duration);
            entry.put("version", versionInfo.version());
            entry.put("commitSha", versionInfo.commit());
            entry.put("exception", exception);

            String line = JsonLine.write(entry);
            accessLog.info("{}", line);
            requestLogStore.append(requestId, entry);
            MDC.clear();
        }
    }
}
