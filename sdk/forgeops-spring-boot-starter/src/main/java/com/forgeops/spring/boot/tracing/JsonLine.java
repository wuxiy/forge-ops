package com.forgeops.spring.boot.tracing;

import java.util.Map;

/**
 * 零依赖 JSON 行输出（仅支持扁平 String/Number/Boolean 值）。
 * 不引 Jackson，避免宿主应用 Jackson 2（Boot 3）与 Jackson 3（Boot 4）版本差异。
 */
public final class JsonLine {

    private JsonLine() {
    }

    public static String write(Map<String, Object> flat) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : flat.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append('"').append(escape(String.valueOf(v))).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String rep;
            switch (c) {
                case '"' -> rep = "\\\"";
                case '\\' -> rep = "\\\\";
                case '\n' -> rep = "\\n";
                case '\r' -> rep = "\\r";
                case '\t' -> rep = "\\t";
                default -> {
                    if (c < 0x20) {
                        if (out == null) {
                            out = new StringBuilder(s.length());
                            out.append(s, 0, i);
                        }
                        out.append(String.format("\\u%04x", (int) c));
                    }
                    continue;
                }
            }
            if (out == null) {
                out = new StringBuilder(s.length());
                out.append(s, 0, i);
            }
            out.append(rep);
        }
        return out == null ? s : out.toString();
    }
}
