package com.forgeops.demoapi.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 内存请求日志缓冲：仅保留最近若干请求的结构化日志条目。
 * GET /api/admin/requests/{requestId} 供 ForgeOps Gateway 按 requestId 拉取（B5 日志补齐）。
 */
@Component
public class RequestLogStore {

    private static final int TRACKED_REQUESTS_MAX = 500;
    private static final int PER_REQUEST_MAX = 50;

    private final Map<String, List<Map<String, Object>>> byRequestId =
            java.util.Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Map<String, Object>>> eldest) {
                    return size() > TRACKED_REQUESTS_MAX;
                }
            });

    public void append(String requestId, Map<String, Object> entry) {
        List<Map<String, Object>> entries = byRequestId.computeIfAbsent(requestId, k -> new ArrayList<>());
        synchronized (entries) {
            if (entries.size() >= PER_REQUEST_MAX) {
                entries.removeFirst();
            }
            entries.add(entry);
        }
    }

    public List<Map<String, Object>> findByRequestId(String requestId) {
        List<Map<String, Object>> entries = byRequestId.get(requestId);
        if (entries == null) {
            return List.of();
        }
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }
}
