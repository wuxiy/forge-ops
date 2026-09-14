package com.company.forgeops.v2.agent.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AgentContractsTest {

    @Test
    void acceptsOnlyExactTriageShapeAndRedactsItsPersistedSummary() throws Exception {
        var parsed = AgentContracts.parseTriage(JsonMapper.shared(),
                "{\"decision\":\"NO_CODE_REQUIRED\",\"summary\":\"email a@example.com token=abc\"}");

        assertEquals(AgentContracts.TriageDecision.NO_CODE_REQUIRED, parsed.decision());
        assertEquals("email [REDACTED] token=[REDACTED]", parsed.summary());
        var canonical = JsonMapper.shared().readTree(AgentContracts.canonicalTriageJson(JsonMapper.shared(), parsed));
        assertEquals("NO_CODE_REQUIRED", canonical.get("decision").asString());
        assertEquals("email [REDACTED] token=[REDACTED]", canonical.get("summary").asString());
    }

    @Test
    void rejectsMissingExtraAndUnknownDecision() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentContracts.parseTriage(JsonMapper.shared(), "{\"decision\":\"NO_CODE_REQUIRED\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentContracts.parseTriage(JsonMapper.shared(),
                        "{\"decision\":\"NO_CODE_REQUIRED\",\"summary\":\"ok\",\"extra\":true}"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentContracts.parseTriage(JsonMapper.shared(), "{\"decision\":\"APPROVE\",\"summary\":\"ok\"}"));
    }
}
