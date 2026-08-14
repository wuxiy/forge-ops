package com.company.forgeops.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.forgeops.context.builder.ContextPackBuilder;
import com.company.forgeops.context.log.HttpLogFetcher;
import com.company.forgeops.feedback.api.dto.FeedbackSubmissionDto;
import com.company.forgeops.feedback.domain.Feedback;
import com.company.forgeops.feedback.domain.FeedbackStatus;
import com.company.forgeops.policy.security.PiiSanitizer;
import com.company.forgeops.project.registry.ProjectConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextPackBuilderTest {

    private final ContextPackBuilder builder = new ContextPackBuilder(
            new HttpLogFetcher(), new PiiSanitizer("/nonexistent"));

    private final ProjectConfig project = new ProjectConfig(
            "demo-app", "Demo App",
            Map.of("enabled", true),
            new ProjectConfig.RepoConfig("vue", "wuxiy/forge-ops", "github", "examples/demo-app/web", "main"),
            new ProjectConfig.RepoConfig("springboot", "wuxiy/forge-ops", "github", "examples/demo-app/api", "main"),
            Map.of(), null,
            new ProjectConfig.MulticaConfig("engineering", "demo-app", "forgeops-triage", "forgeops-coding"),
            Map.of(), Map.of(), Map.of("autoAnalyze", true), Map.of());

    @Test
    void buildsSchemaCompliantPack() {
        Feedback feedback = new Feedback();
        feedback.setProjectId("demo-app");
        feedback.setType("BUG");
        feedback.setTitle("检查记录页面持续 loading");
        feedback.setDescription("患者手机号 13812345678 查询空数据页面一直转圈");
        feedback.setReporterName("测试同学");
        feedback.setEnvironment("test");
        feedback.setStatus(FeedbackStatus.CONTEXT_BUILDING);
        feedback.setFrontendVersion("0.1.0");

        FeedbackSubmissionDto submission = new FeedbackSubmissionDto(
                "1.0", "demo-app", "BUG", null,
                "患者查询空数据页面一直转圈", "空数据展示空态", null, null,
                new FeedbackSubmissionDto.Reporter("u1", "测试同学"), "test",
                new FeedbackSubmissionDto.Page("http://localhost:5173/", "home", "检查记录"),
                null,
                new FeedbackSubmissionDto.VersionInfo("0.1.0", "abc1234"),
                new FeedbackSubmissionDto.VersionInfo("0.1.0", "def5678"),
                List.of(new FeedbackSubmissionDto.RequestSummary(
                        "GET", "/api/patients/P-99999/records", 500, 42, "01JTEST", null, "2026-08-15T00:00:00Z")),
                List.of("[Error] Uncaught"),
                null, "2026-08-15T00:00:00Z");

        Map<String, Object> pack = builder.build(feedback, submission, project);

        assertEquals("1.0", pack.get("schemaVersion"));
        assertNotNull(pack.get("feedback"));
        assertNotNull(pack.get("git"));
        assertEquals("github", ((Map<?, ?>) pack.get("git")).get("repoProvider"));
        assertEquals("wuxiy/forge-ops", ((Map<?, ?>) pack.get("git")).get("frontendRepo"));
        assertTrue(pack.get("requests") instanceof List<?> requests && requests.size() == 1);
        // PII 已脱敏（手机号不进入 Context Pack）
        String packJson = com.company.forgeops.context.builder.ContextPackJson.toJson(pack);
        assertTrue(packJson.contains(PiiSanitizer.MASK));
        assertTrue(!packJson.contains("13812345678"));
    }
}
