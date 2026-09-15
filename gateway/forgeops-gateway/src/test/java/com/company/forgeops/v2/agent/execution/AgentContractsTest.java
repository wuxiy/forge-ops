package com.company.forgeops.v2.agent.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AgentContractsTest {

    @Test
    void acceptsOnlyExactTriageShapeAndRedactsItsPersistedSummary() throws Exception {
        var parsed = AgentContracts.parseTriage(JsonMapper.shared(),
                "{\"decision\":\"NO_CODE_REQUIRED\",\"summary\":\"email a@example.com token=abc\",\"rootCause\":\"not a code issue\",\"evidence\":[\"route /probe\"],\"relatedFiles\":[],\"missingInformation\":[],\"risks\":[],\"suggestedPlan\":[]}");

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
                        "{\"decision\":\"NO_CODE_REQUIRED\",\"summary\":\"ok\",\"rootCause\":\"x\",\"evidence\":[],\"relatedFiles\":[],\"missingInformation\":[],\"risks\":[],\"suggestedPlan\":[],\"extra\":true}"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentContracts.parseTriage(JsonMapper.shared(), "{\"decision\":\"APPROVE\",\"summary\":\"ok\",\"rootCause\":\"x\",\"evidence\":[],\"relatedFiles\":[],\"missingInformation\":[],\"risks\":[],\"suggestedPlan\":[]}"));
    }

    @Test
    void classifiesCompleteCodingDeclarationsWithoutTrustingDeliveryClaims() {
        var prCreated = AgentContracts.parseCoding(JsonMapper.shared(),
                "{\"outcome\":\"PR_CREATED\",\"branch\":\"forgeops/v2-fix\",\"commitSha\":\"abc123\",\"prUrl\":\"https://example.invalid/pr/1\",\"changedFiles\":[\"src/App.java\"],\"tests\":[\"mvn test\"],\"risks\":[],\"failureCategory\":null,\"failureMessage\":null}");
        var noChange = AgentContracts.parseCoding(JsonMapper.shared(),
                "{\"outcome\":\"NO_CHANGE\",\"branch\":null,\"commitSha\":null,\"prUrl\":null,\"changedFiles\":[],\"tests\":[],\"risks\":[],\"failureCategory\":null,\"failureMessage\":null}");
        var failed = AgentContracts.parseCoding(JsonMapper.shared(),
                "{\"outcome\":\"FAILED\",\"branch\":null,\"commitSha\":null,\"prUrl\":null,\"changedFiles\":[],\"tests\":[],\"risks\":[],\"failureCategory\":\"TEST_FAILURE\",\"failureMessage\":\"unit test failed\"}");

        assertEquals(AgentContracts.CodingOutcome.PR_CREATED, prCreated.outcome());
        assertEquals("https://example.invalid/pr/1", prCreated.prUrl());
        assertEquals(AgentContracts.CodingOutcome.NO_CHANGE, noChange.outcome());
        assertEquals(AgentContracts.CodingOutcome.FAILED, failed.outcome());
    }

    @Test
    void rejectsIncompleteOrInternallyInconsistentCodingDeclarations() {
        assertThrows(IllegalArgumentException.class, () -> AgentContracts.parseCoding(JsonMapper.shared(),
                "{\"outcome\":\"PR_CREATED\",\"branch\":\"branch\",\"commitSha\":null,\"prUrl\":null,\"changedFiles\":[],\"tests\":[],\"risks\":[],\"failureCategory\":null,\"failureMessage\":null}"));
        assertThrows(IllegalArgumentException.class, () -> AgentContracts.parseCoding(JsonMapper.shared(),
                "{\"outcome\":\"FAILED\",\"branch\":null,\"commitSha\":null,\"prUrl\":null,\"changedFiles\":[],\"tests\":[],\"risks\":[],\"failureCategory\":null,\"failureMessage\":null}"));
        assertThrows(IllegalArgumentException.class, () -> AgentContracts.parseCoding(JsonMapper.shared(),
                "{\"outcome\":\"NO_CHANGE\",\"branch\":null,\"commitSha\":null,\"prUrl\":null,\"changedFiles\":[],\"tests\":[],\"risks\":[],\"failureCategory\":null,\"failureMessage\":null,\"forged\":true}"));
    }
}
