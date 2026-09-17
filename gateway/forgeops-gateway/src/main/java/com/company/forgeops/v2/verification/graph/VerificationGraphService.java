package com.company.forgeops.v2.verification.graph;

import com.company.forgeops.v2.verification.domain.VerificationEdge;
import com.company.forgeops.v2.verification.domain.VerificationEdgeRepository;
import com.company.forgeops.v2.verification.domain.VerificationNode;
import com.company.forgeops.v2.verification.domain.VerificationNodeRepository;
import com.company.forgeops.v2.verification.domain.VerificationProvenance;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * ADR-0007 graph V1. The impact set is computed in place from a baseline snapshot plus the file diff.
 * LLM-provenance edges may only expand the closure; the traversal never uses them to drop nodes.
 */
@Service
public class VerificationGraphService {

    public record ImpactResult(Set<String> files, Set<String> modules, Set<String> capabilities,
            Set<String> testSuiteCategories, String baselineCommit, String fallbackReason,
            List<String> appliedLlmEdges) {
    }

    private final VerificationNodeRepository nodes;
    private final VerificationEdgeRepository edges;

    public VerificationGraphService(VerificationNodeRepository nodes, VerificationEdgeRepository edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    /**
     * Conservative fallback (VER-20): a missing, unparsable or stale graph yields an empty impact set and a
     * fallback reason; the caller then runs every required category at full strength.
     */
    public ImpactResult impactSet(String projectId, List<String> diffFiles, int graphMaxAgeDays,
            Integer mergesSinceBaseline, int graphMaxMergeLag) {
        Optional<String> baseline = latestBaseline(projectId);
        if (baseline.isEmpty()) {
            return fallback(diffFiles, null, "GRAPH_MISSING");
        }
        String baselineCommit = baseline.get();
        List<VerificationNode> snapshot = nodes.findByProjectIdAndBaselineCommit(projectId, baselineCommit);
        if (snapshot.isEmpty()) {
            return fallback(diffFiles, baselineCommit, "GRAPH_PARSE_FAILED");
        }
        OffsetDateTime capturedAt = snapshot.stream().map(VerificationNode::getCapturedAt)
                .max(OffsetDateTime::compareTo).orElseThrow();
        if (capturedAt.isBefore(OffsetDateTime.now().minus(Duration.ofDays(graphMaxAgeDays)))) {
            return fallback(diffFiles, baselineCommit, "GRAPH_BASELINE_EXPIRED_AGE");
        }
        if (mergesSinceBaseline != null && mergesSinceBaseline > graphMaxMergeLag) {
            return fallback(diffFiles, baselineCommit, "GRAPH_BASELINE_EXPIRED_MERGE_LAG");
        }
        return traverse(projectId, diffFiles, snapshot, baselineCommit);
    }

    private ImpactResult traverse(String projectId, List<String> diffFiles, List<VerificationNode> snapshot,
            String baselineCommit) {
        Set<String> fileNodes = new LinkedHashSet<>();
        Set<UUID> frontier = new LinkedHashSet<>();
        for (VerificationNode node : snapshot) {
            if (node.getType() == VerificationNode.Type.FILE && diffFiles.contains(node.getPath())) {
                fileNodes.add(node.getPath());
                frontier.add(node.getId());
            }
        }
        Set<UUID> visited = new HashSet<>(frontier);
        Set<String> modules = new LinkedHashSet<>();
        Set<String> capabilities = new LinkedHashSet<>();
        Set<String> categories = new LinkedHashSet<>();
        List<String> appliedLlmEdges = new java.util.ArrayList<>();
        Deque<UUID> queue = new ArrayDeque<>(frontier);
        List<VerificationEdge> allEdges = edges.findByProjectId(projectId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (VerificationEdge edge : allEdges) {
                if (!edge.getFromNodeId().equals(current) || !visited.add(edge.getToNodeId())) {
                    continue;
                }
                Optional<VerificationNode> target = snapshot.stream()
                        .filter(node -> node.getId().equals(edge.getToNodeId())).findFirst();
                if (target.isEmpty()) {
                    continue;
                }
                if (edge.getProvenance() == VerificationProvenance.LLM) {
                    appliedLlmEdges.add(edge.getId().toString());
                }
                switch (target.get().getType()) {
                    case MODULE -> modules.add(target.get().getName());
                    case CAPABILITY -> capabilities.add(target.get().getName());
                    case TEST_SUITE -> categories.add(target.get().getName());
                    default -> { }
                }
                queue.add(edge.getToNodeId());
            }
        }
        return new ImpactResult(fileNodes, modules, capabilities, categories, baselineCommit, null,
                List.copyOf(appliedLlmEdges));
    }

    private Optional<String> latestBaseline(String projectId) {
        return nodes.findAll().stream()
                .filter(node -> node.getProjectId().equals(projectId))
                .max(java.util.Comparator.comparing(VerificationNode::getCapturedAt))
                .map(VerificationNode::getBaselineCommit);
    }

    private static ImpactResult fallback(List<String> diffFiles, String baselineCommit, String reason) {
        return new ImpactResult(new LinkedHashSet<>(diffFiles), Set.of(), Set.of(), Set.of(), baselineCommit, reason,
                List.of());
    }
}
