package com.company.forgeops.v2.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.forgeops.v2.agent.execution.AgentContracts;
import com.company.forgeops.v2.agent.execution.AgentContracts.CategorySelection;
import com.company.forgeops.v2.agent.execution.AgentContracts.VerificationPlanResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** VER-04: only a complete, exactly-shaped planner output becomes a plan; everything else is invalid output. */
class AgentContractsVerificationSchemaTest {

    private static final String VALID = """
            {"riskLevel":"MEDIUM","summary":"impact-driven plan",
             "selectedCategories":[{"category":"BUILD","reason":"always required"}],
             "skippedCategories":[]}""";

    @Test
    void validPlanParsesWithSelections() {
        VerificationPlanResult result = AgentContracts.parseVerificationPlan(JsonMapper.shared(), VALID);
        assertEquals("MEDIUM", result.riskLevel());
        assertEquals(List.of(new CategorySelection("BUILD", "always required")), result.selectedCategories());
        assertTrue(result.skippedCategories().isEmpty());
    }

    @Test
    void missingRequiredFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AgentContracts.parseVerificationPlan(JsonMapper.shared(),
                "{\"riskLevel\":\"LOW\",\"summary\":\"s\",\"selectedCategories\":[]}"));
    }

    @Test
    void extraFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AgentContracts.parseVerificationPlan(JsonMapper.shared(),
                VALID.replace("\"skippedCategories\":[]", "\"skippedCategories\":[],\"gateVerdict\":\"PASS\"")));
    }

    @Test
    void invalidRiskEnumIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AgentContracts.parseVerificationPlan(JsonMapper.shared(),
                VALID.replace("MEDIUM", "CATASTROPHIC")));
    }

    @Test
    void duplicateCategoryInOneListIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AgentContracts.parseVerificationPlan(JsonMapper.shared(),
                "{\"riskLevel\":\"LOW\",\"summary\":\"s\",\"selectedCategories\":["
                        + "{\"category\":\"BUILD\",\"reason\":\"a\"},{\"category\":\"BUILD\",\"reason\":\"b\"}],"
                        + "\"skippedCategories\":[]}"));
    }

    @Test
    void secretLookingReasonsAreRedacted() {
        VerificationPlanResult result = AgentContracts.parseVerificationPlan(JsonMapper.shared(),
                "{\"riskLevel\":\"LOW\",\"summary\":\"s\",\"selectedCategories\":["
                        + "{\"category\":\"BUILD\",\"reason\":\"token=abcdef123456\"}],\"skippedCategories\":[]}");
        assertEquals("token=[REDACTED]", result.selectedCategories().getFirst().reason());
    }

    @Test
    void failureTriageSchemaIsEquallyStrict() {
        assertThrows(IllegalArgumentException.class, () -> AgentContracts.parseFailureTriage(JsonMapper.shared(),
                "{\"category\":\"NOT_A_CATEGORY\",\"summary\":\"s\",\"evidence\":[],\"suggestedAction\":\"a\"}"));
        var result = AgentContracts.parseFailureTriage(JsonMapper.shared(),
                "{\"category\":\"FLAKY_TEST\",\"summary\":\"s\",\"evidence\":[\"re-run passed\"],\"suggestedAction\":\"quarantine\"}");
        assertEquals("FLAKY_TEST", result.category());
    }
}
