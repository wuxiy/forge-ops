package com.forgeops.demoapi.web;

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
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * ForgeOps Request-ID 链路（架构文档 §8）：
 * 浏览器/SDK 注入 X-Request-ID（ULID），本 Filter 透传或补生成，
 * 写入 MDC 并输出结构化 JSON 访问日志，同时存入内存 RequestLogStore
 * 供 ForgeOps Gateway 按 requestId 拉取日志摘录。
 */
@Component
@Order(1)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-ID";
    public static final String HEADER_TRACE_ID = "X-Trace-ID";

    private static final Logger accessLog = LoggerFactory.getLogger("ACCESS");

    private final RequestLogStore requestLogStore;
    private final VersionProvider versionProvider;

    public RequestIdFilter(RequestLogStore requestLogStore, VersionProvider versionProvider) {
        this.requestLogStore = requestLogStore;
        this.versionProvider = versionProvider;
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
            String exception = failure == null ? null : failure.getClass().getSimpleName() + ": " + failure.getMessage();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ts", Instant.now().toString());
            entry.put("requestId", requestId);
            entry.put("traceId", traceId == null ? "" : traceId);
            entry.put("service", "demo-api");
            entry.put("uri", uri);
            entry.put("httpMethod", method);
            entry.put("status", status);
            entry.put("durationMs", duration);
            entry.put("version", versionProvider.version());
            entry.put("commitSha", versionProvider.commit());
            entry.put("exception", exception == null ? "" : exception);

            accessLog.info("{}", Json.write(entry));
            requestLogStore.append(requestId, entry);
            MDC.clear();
        }
    }
}
