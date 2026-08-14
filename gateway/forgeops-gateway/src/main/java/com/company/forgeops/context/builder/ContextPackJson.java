package com.company.forgeops.context.builder;

import tools.jackson.databind.json.JsonMapper;

/** Context Pack JSON 序列化（Jackson 3）。 */
public final class ContextPackJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ContextPackJson() {
    }

    public static String toJson(Object value) {
        return MAPPER.writeValueAsString(value);
    }
}
