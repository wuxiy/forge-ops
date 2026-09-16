package com.company.forgeops.v2.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.forgeops.v2.verification.gate.VerificationGate;
import com.company.forgeops.v2.verification.gate.VerificationGate.Decision;
import com.company.forgeops.v2.verification.gate.VerificationGate.EvidenceFact;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * VER-02/03/08/12/13: pure-function proofs for the deterministic gate. LLM-produced verdicts are structurally
 * absent from EvidenceFact, stale and post-merge facts never gate, and identical inputs decide identically.
 */
class VerificationGateTest {

    private static final String HEAD = "abc123";

    private static EvidenceFact fact(String category, String source, String sha, String conclusion) {
        return new EvidenceFact(category, source, sha, conclusion, "digest-" + category + source + sha + conclusion);
    }

    @Test
    void completeCurrentPrEvidencePassesDeterministically() {
        var facts = List.of(fact("BUILD", "PR_CHECK", HEAD, "SUCCESS"));
        for (int i = 0; i < 100; i++) {
            var evaluation = VerificationGate.evaluate(List.of("BUILD"), facts, HEAD);
            assertEquals(Decision.PASS, evaluation.decision());
            assertTrue(evaluation.autoMergeEligible());
            assertTrue(evaluation.reasons().isEmpty());
        }
    }

    @Test
    void ver08anLlmVerdictFieldCannotInfluenceAFailingGate() {
        // An LLM "PASS" can only live in payload text; the gate reads no such field, so a failed check still blocks.
        var facts = List.of(fact("BUILD", "PR_CHECK", HEAD, "FAILURE"));
        var evaluation = VerificationGate.evaluate(List.of("BUILD"), facts, HEAD);
        assertEquals(Decision.BLOCK, evaluation.decision());
        assertEquals(List.of("CATEGORY_FAILED:BUILD"), evaluation.reasons());
        assertFalse(evaluation.autoMergeEligible());
    }

    @Test
    void ver02staleHeadShaEvidenceNeverGates() {
        var facts = List.of(fact("BUILD", "PR_CHECK", "old-sha", "SUCCESS"));
        var evaluation = VerificationGate.evaluate(List.of("BUILD"), facts, HEAD);
        assertEquals(Decision.BLOCK, evaluation.decision());
        assertEquals(List.of("CATEGORY_MISSING:BUILD"), evaluation.reasons());
    }

    @Test
    void ver03postMergeCheckCannotServeAsPrEvidence() {
        var facts = List.of(fact("BUILD", "POST_MERGE_CHECK", HEAD, "SUCCESS"));
        var evaluation = VerificationGate.evaluate(List.of("BUILD"), facts, HEAD);
        assertEquals(Decision.BLOCK, evaluation.decision());
        assertEquals(List.of("CATEGORY_MISSING:BUILD"), evaluation.reasons());
    }

    @Test
    void ver13warnSkippedEvidenceIsNeverAutoMergeEligible() {
        var facts = List.of(fact("BUILD", "PR_CHECK", HEAD, "NEUTRAL"));
        var evaluation = VerificationGate.evaluate(List.of("BUILD"), facts, HEAD);
        assertEquals(Decision.WARN, evaluation.decision());
        assertEquals(List.of("CATEGORY_SKIPPED:BUILD"), evaluation.reasons());
        assertFalse(evaluation.autoMergeEligible());
    }

    @Test
    void missingCategoryBlocksAndExtraCategoriesDoNotRelaxIt() {
        var evaluation = VerificationGate.evaluate(List.of("BUILD", "E2E"),
                List.of(fact("BUILD", "PR_CHECK", HEAD, "SUCCESS"), fact("SMOKE", "EXECUTOR", HEAD, "SUCCESS")), HEAD);
        assertEquals(Decision.BLOCK, evaluation.decision());
        assertEquals(List.of("CATEGORY_MISSING:E2E"), evaluation.reasons());
    }

    @Test
    void ver12qualityPolicyTighteningExplainsEveryDecisionChange() {
        var facts = List.of(fact("BUILD", "PR_CHECK", HEAD, "SUCCESS"));
        var permissive = VerificationGate.evaluate(List.of("BUILD"), facts, HEAD);
        var tightened = VerificationGate.evaluate(List.of("BUILD", "E2E"), facts, HEAD);
        assertEquals(Decision.PASS, permissive.decision());
        assertEquals(Decision.BLOCK, tightened.decision());
        assertEquals(List.of("CATEGORY_MISSING:E2E"), tightened.reasons());
    }

    @Test
    void failedEvidenceBlocksEvenWhenAnotherFactSucceeded() {
        var facts = List.of(fact("BUILD", "PR_CHECK", HEAD, "SUCCESS"), fact("BUILD", "PR_CHECK", HEAD, "FAILURE"));
        var evaluation = VerificationGate.evaluate(List.of("BUILD"), facts, HEAD);
        assertEquals(Decision.BLOCK, evaluation.decision());
        assertEquals("FAILURE", evaluation.categoryOutcomes().get("BUILD"));
    }
}
