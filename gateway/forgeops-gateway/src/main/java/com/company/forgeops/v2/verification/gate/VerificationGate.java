package com.company.forgeops.v2.verification.gate;

import com.company.forgeops.v2.verification.domain.VerificationEvidence;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ADR-0002/0005/0006: the deterministic gate. It is a pure function of (required categories, evidence facts,
 * plan head SHA). LLM-produced fields are not part of {@link EvidenceFact} and can therefore never influence
 * a decision (VER-08). Identical inputs always yield an identical decision (VER-12).
 */
public final class VerificationGate {

    public enum Decision { PASS, WARN, BLOCK }

    public record EvidenceFact(String category, String source, String headSha, String conclusion, String payloadDigest) {

        public static EvidenceFact from(VerificationEvidence evidence) {
            return new EvidenceFact(evidence.getCategory(), evidence.getSource(), evidence.getHeadSha(),
                    evidence.getConclusion(), evidence.getPayloadDigest());
        }

        public boolean gatesPr() {
            return "PR_CHECK".equals(source) || "EXECUTOR".equals(source);
        }

        public boolean currentFor(String planHeadSha) {
            return headSha != null && headSha.equals(planHeadSha);
        }
    }

    public record Evaluation(Decision decision, List<String> reasons, Map<String, String> categoryOutcomes) {

        public boolean autoMergeEligible() {
            return decision == Decision.PASS;
        }
    }

    private VerificationGate() {
    }

    public static Evaluation evaluate(List<String> requiredCategories, List<EvidenceFact> facts, String planHeadSha) {
        Map<String, String> outcomes = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();
        Decision decision = Decision.PASS;
        for (String category : requiredCategories) {
            List<EvidenceFact> current = facts.stream()
                    .filter(fact -> category.equals(fact.category()))
                    .filter(EvidenceFact::gatesPr)
                    .filter(fact -> fact.currentFor(planHeadSha))
                    .toList();
            if (current.stream().anyMatch(fact -> "FAILURE".equals(fact.conclusion()))) {
                outcomes.put(category, "FAILURE");
                reasons.add("CATEGORY_FAILED:" + category);
                decision = Decision.BLOCK;
                continue;
            }
            if (current.stream().anyMatch(fact -> "SUCCESS".equals(fact.conclusion()))) {
                outcomes.put(category, "SUCCESS");
                continue;
            }
            if (current.stream().anyMatch(fact -> "NEUTRAL".equals(fact.conclusion()))) {
                outcomes.put(category, "SKIPPED");
                reasons.add("CATEGORY_SKIPPED:" + category);
                if (decision != Decision.BLOCK) {
                    decision = Decision.WARN;
                }
                continue;
            }
            outcomes.put(category, "MISSING");
            reasons.add("CATEGORY_MISSING:" + category);
            decision = Decision.BLOCK;
        }
        return new Evaluation(decision, List.copyOf(reasons), Map.copyOf(outcomes));
    }
}
