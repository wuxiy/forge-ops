package com.company.forgeops.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.forgeops.policy.security.PiiSanitizer;
import org.junit.jupiter.api.Test;

class PiiSanitizerTest {

    private final PiiSanitizer sanitizer = new PiiSanitizer("/nonexistent/pii-policy.yaml");

    @Test
    void masksChinaIdCard() {
        String masked = sanitizer.sanitize("患者 11010119900307867X 登录失败");
        assertTrue(masked.contains(PiiSanitizer.MASK));
        assertEquals("患者 ***MASKED*** 登录失败", masked);
    }

    @Test
    void masksChinaMobile() {
        String masked = sanitizer.sanitize("联系电话 13812345678 无法接通");
        assertEquals("联系电话 ***MASKED*** 无法接通", masked);
    }

    @Test
    void masksMedicalRecordNoKeepingPrefix() {
        String masked = sanitizer.sanitize("病历号: MR20260001 打不开");
        assertEquals("病历号: ***MASKED*** 打不开", masked);
    }

    @Test
    void masksEmail() {
        String masked = sanitizer.sanitize("账号 test.user@example.com 报错");
        assertTrue(masked.contains(PiiSanitizer.MASK));
    }

    @Test
    void keepsNormalText() {
        assertEquals("检查记录页面持续 loading", sanitizer.sanitize("检查记录页面持续 loading"));
    }
}
