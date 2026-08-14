package com.company.forgeops.policy.security;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * PII Sanitizer（§19.1）：对自由文本做正则脱敏 —— Context Pack 进入 LLM 前的第二道防线
 * （第一道为 SDK 白名单采集）。默认模式与 policy/pii-policy.yaml 对齐。
 */
@Component
public class PiiSanitizer {

    private static final Logger log = LoggerFactory.getLogger(PiiSanitizer.class);

    public static final String MASK = "***MASKED***";

    private final List<Pattern> patterns = new ArrayList<>();

    private final String policyPath;

    public PiiSanitizer(@Value("${forgeops.pii.policy-path:policy/pii-policy.yaml}") String policyPath) {
        this.policyPath = policyPath;
        registerDefaults();
    }

    @PostConstruct
    void init() {
        registerDefaults();
        try {
            Path path = Path.of(policyPath);
            if (Files.isRegularFile(path)) {
                Map<String, Object> root = new Yaml().load(Files.newBufferedReader(path));
                Object profiles = root == null ? null : root.get("profiles");
                if (profiles instanceof Map<?, ?> profileMap) {
                    for (Object profile : profileMap.values()) {
                        if (profile instanceof Map<?, ?> p && p.get("sanitizer") instanceof Map<?, ?> s
                                && s.get("maskPatterns") instanceof Map<?, ?> maskPatterns) {
                            for (Object regex : maskPatterns.values()) {
                                if (regex instanceof String r && !r.isBlank()) {
                                    patterns.add(Pattern.compile(r));
                                }
                            }
                        }
                    }
                }
                log.info("PII 策略已加载: {} (模式数={})", path, patterns.size());
            } else {
                log.info("PII 策略文件不存在({})，使用内置默认模式", path);
            }
        } catch (Exception e) {
            log.warn("PII 策略加载失败，使用内置默认模式: {}", e.getMessage());
        }
    }

    private void registerDefaults() {
        patterns.add(Pattern.compile("\\b[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]\\b"));
        patterns.add(Pattern.compile("\\b1[3-9]\\d{9}\\b"));
        patterns.add(Pattern.compile("(?i)(病历号[:：]?\\s*)([A-Za-z0-9-]{4,})"));
        patterns.add(Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"));
    }

    /** 对单段自由文本脱敏；病历号保留前缀。 */
    public String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String output = input;
        for (Pattern pattern : patterns) {
            // 病历号保留前缀，其余整体替换
            if (pattern.pattern().contains("病历号")) {
                output = pattern.matcher(output).replaceAll("$1" + MASK);
            } else {
                output = pattern.matcher(output).replaceAll(MASK);
            }
        }
        return output;
    }

    public Map<String, String> sanitizeMap(Map<String, String> fields) {
        Map<String, String> result = new LinkedHashMap<>();
        fields.forEach((key, value) -> result.put(key, sanitize(value)));
        return result;
    }

    boolean isMasked(String text) {
        return text != null && text.contains(MASK);
    }
}
