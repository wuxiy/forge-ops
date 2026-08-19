package com.company.forgeops.feedback.domain;

import java.util.regex.Pattern;

/**
 * 反馈标识解析（全链路统一入口：SDK 展示、回调 feedbackId、API 路径参数）。
 * 支持两种格式：
 *   新：<PREFIX>-FB-<no>   如 ADB-FB-1001（项目前缀 + 项目内序号）
 *   旧：FB-<no>            如 FB-1002（未配置前缀的项目 / 历史数据）
 */
public final class FeedbackIdentifier {

    private static final Pattern PATTERN = Pattern.compile("^(?:([A-Za-z0-9]{2,16})-)?FB-(\\d+)$");

    private FeedbackIdentifier() {
    }

    public record Parsed(String prefix, long displayNo) {
    }

    /** 不匹配返回 null（调用方自行报参数错误）。 */
    public static Parsed parse(String identifier) {
        if (identifier == null) return null;
        var m = PATTERN.matcher(identifier.trim());
        if (!m.matches()) return null;
        String prefix = m.group(1) == null ? null : m.group(1).toUpperCase();
        return new Parsed(prefix, Long.parseLong(m.group(2)));
    }
}
