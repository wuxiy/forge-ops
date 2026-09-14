package com.company.forgeops.v2.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Fail-closed context boundary. It accepts a small allowlist, removes credentials and PII, and
 * creates the exact JSON persisted in ContextSnapshot. Raw input must not cross this boundary.
 */
@Service
public class ContextPreparation {

    public static final String MASK = "[REDACTED]";
    private static final Set<String> ALLOWED_FIELDS = Set.of("url", "route", "userAction", "console", "requestSummary");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[a-z0-9._~+/-]+=*");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(password|passwd|secret|token|authorization|cookie|set-cookie|api[_-]?key)\\s*([:=])\\s*[^\\s,;\\\"}]+");
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("\\b1[3-9]\\d{9}\\b");
    private static final Pattern CHINA_ID = Pattern.compile("\\b[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]\\b");

    public PreparedContext prepare(String title, String description, Map<String, String> browserContext) {
        if (blank(title) || blank(description)) {
            throw new IllegalArgumentException("title and description are required");
        }
        Map<String, String> input = browserContext == null ? Map.of() : browserContext;
        if (!ALLOWED_FIELDS.containsAll(input.keySet()) || input.entrySet().stream().anyMatch(entry -> blank(entry.getValue()))) {
            throw new IllegalArgumentException("browser context contains an unsupported or blank field");
        }

        Redaction titleResult = redact(title);
        Redaction descriptionResult = redact(description);
        Map<String, String> cleaned = new LinkedHashMap<>();
        int redactionCount = titleResult.count + descriptionResult.count;
        for (String field : ALLOWED_FIELDS.stream().sorted().toList()) {
            String value = input.get(field);
            if (value != null) {
                Redaction result = redact(value);
                cleaned.put(field, result.value);
                redactionCount += result.count;
            }
        }

        String contentJson = toJson(titleResult.value, descriptionResult.value, cleaned);
        return new PreparedContext(titleResult.value, descriptionResult.value, contentJson, sha256(contentJson), redactionCount);
    }

    private static Redaction redact(String raw) {
        String value = raw;
        int count = 0;
        Replacement bearer = replace(value, BEARER, MASK);
        value = bearer.value;
        count += bearer.count;
        Replacement secrets = replace(value, SECRET_ASSIGNMENT, "$1$2" + MASK);
        value = secrets.value;
        count += secrets.count;
        Replacement emails = replace(value, EMAIL, MASK);
        value = emails.value;
        count += emails.count;
        Replacement phones = replace(value, PHONE, MASK);
        value = phones.value;
        count += phones.count;
        Replacement ids = replace(value, CHINA_ID, MASK);
        return new Redaction(ids.value, count + ids.count);
    }

    private static Replacement replace(String value, Pattern pattern, String replacement) {
        var matcher = pattern.matcher(value);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return new Replacement(pattern.matcher(value).replaceAll(replacement), count);
    }

    private static String toJson(String title, String description, Map<String, String> context) {
        StringBuilder json = new StringBuilder("{\"title\":\"").append(escape(title)).append("\",\"description\":\"")
                .append(escape(description)).append("\",\"browserContext\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : context.entrySet()) {
            if (!first) {
                json.append(',');
            }
            json.append('\"').append(entry.getKey()).append("\":\"").append(escape(entry.getValue())).append('\"');
            first = false;
        }
        return json.append("}}").toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record Redaction(String value, int count) {
    }

    private record Replacement(String value, int count) {
    }
}
