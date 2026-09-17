package com.company.forgeops.v2.context;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextPreparationTest {

    private final ContextPreparation preparation = new ContextPreparation();

    @Test
    void removesCredentialsAndPiiFromEveryAcceptedField() {
        String secret = "super-secret-token-123";
        PreparedContext prepared = preparation.prepare("登录失败 token=" + secret,
                "联系人 test.user@example.com，手机 13812345678，Authorization: Bearer " + secret,
                Map.of("url", "https://example.test/orders?token=" + secret,
                        "console", "cookie=session=" + secret));

        assertFalse(prepared.contentJson().contains(secret));
        assertFalse(prepared.contentJson().contains("test.user@example.com"));
        assertFalse(prepared.contentJson().contains("13812345678"));
        assertTrue(prepared.redactionCount() >= 4);
        assertTrue(prepared.contentJson().contains(ContextPreparation.MASK));
    }

    @Test
    void rejectsUnapprovedBrowserFieldsInsteadOfStoringThem() {
        assertThrows(IllegalArgumentException.class, () -> preparation.prepare("title", "description",
                Map.of("authorization", "Bearer should-not-be-accepted")));
    }
}
