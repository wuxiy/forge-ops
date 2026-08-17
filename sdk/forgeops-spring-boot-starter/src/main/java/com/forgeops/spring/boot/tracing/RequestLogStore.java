package com.forgeops.spring.boot.tracing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存请求日志缓冲（按 requestId 归档最近请求的结构化日志行）。
 * ForgeOps Gateway Context Pack 经 /api/admin/requests/{requestId} 按 ID 精确拉取。
 */
public class RequestLogStore {

    private final int maxTracked;
    private final int perRequestMax = 50;
    private final Map<String, List<Map<String, Object>>> byRequestId;

    public RequestLogStore(int maxTracked) {
        this.maxTracked = Math.max(16, maxTracked);
        this.byRequestId = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<Map<String, Object>>> eldest) {
                return size() > maxTracked;
            }
        });
    }

    public void append(String requestId, Map<String, Object> entry) {
        List<Map<String, Object>> entries = byRequestId.computeIfAbsent(requestId, k -> new ArrayList<>());
        synchronized (entries) {
            if (entries.size() >= perRequestMax) {
                entries.remove(0);
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
