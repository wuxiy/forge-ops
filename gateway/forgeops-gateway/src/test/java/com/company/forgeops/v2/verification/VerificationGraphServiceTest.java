package com.company.forgeops.v2.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.company.forgeops.v2.verification.domain.VerificationEdge;
import com.company.forgeops.v2.verification.domain.VerificationEdgeRepository;
import com.company.forgeops.v2.verification.domain.VerificationNode;
import com.company.forgeops.v2.verification.domain.VerificationNodeRepository;
import com.company.forgeops.v2.verification.domain.VerificationNode.Type;
import com.company.forgeops.v2.verification.domain.VerificationProvenance;
import com.company.forgeops.v2.verification.graph.VerificationGraphService;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** VER-18/19/20: impact-set closure, LLM-edge monotonic expansion and conservative fallbacks (ADR-0007). */
class VerificationGraphServiceTest {

    private final Map<UUID, VerificationNode> nodeStore = new ConcurrentHashMap<>();
    private final Map<UUID, VerificationEdge> edgeStore = new ConcurrentHashMap<>();
    private VerificationGraphService service;

    @BeforeEach
    void setUp() {
        VerificationNodeRepository nodes = Mockito.mock(VerificationNodeRepository.class);
        VerificationEdgeRepository edges = Mockito.mock(VerificationEdgeRepository.class);
        when(nodes.save(any())).thenAnswer(invocation -> {
            VerificationNode node = invocation.getArgument(0);
            nodeStore.put(node.getId(), node);
            return node;
        });
        when(nodes.findAll()).thenAnswer(invocation -> List.copyOf(nodeStore.values()));
        when(nodes.findByProjectIdAndBaselineCommit(any(), any())).thenAnswer(invocation ->
                nodeStore.values().stream().filter(node -> node.getProjectId().equals(invocation.getArgument(0))
                        && node.getBaselineCommit().equals(invocation.getArgument(1))).toList());
        when(nodes.findByProjectIdAndTypeAndPathAndBaselineCommit(any(), any(), any(), any())).thenAnswer(invocation ->
                nodeStore.values().stream().filter(node -> node.getProjectId().equals(invocation.getArgument(0))
                        && node.getType() == invocation.getArgument(1, Type.class)
                        && node.getPath().equals(invocation.getArgument(2))
                        && node.getBaselineCommit().equals(invocation.getArgument(3))).findFirst());
        when(edges.save(any())).thenAnswer(invocation -> {
            VerificationEdge edge = invocation.getArgument(0);
            edgeStore.put(edge.getId(), edge);
            return edge;
        });
        when(edges.findByProjectId(any())).thenAnswer(invocation ->
                edgeStore.values().stream().filter(edge -> edge.getProjectId().equals(invocation.getArgument(0))).toList());
        service = new VerificationGraphService(nodes, edges);
        seed();
    }

    private void seed() {
        VerificationNode file = save("pilot", Type.FILE, "src/App.java", "App.java", "base-1");
        VerificationNode module = save("pilot", Type.MODULE, "src/", "ui", "base-1");
        VerificationNode capability = save("pilot", Type.CAPABILITY, "cap:login", "login", "base-1");
        VerificationNode suite = save("pilot", Type.TEST_SUITE, "tests/login.spec.ts", "E2E", "base-1");
        link("pilot", file, module, VerificationProvenance.STATIC);
        link("pilot", module, capability, VerificationProvenance.STATIC);
        link("pilot", capability, suite, VerificationProvenance.HUMAN);
    }

    @Test
    void ver18impactSetContainsEveryLinkedSuiteAndCapability() {
        var impact = service.impactSet("pilot", List.of("src/App.java"), 7, null, 50);
        assertEquals("base-1", impact.baselineCommit());
        assertTrue(impact.files().contains("src/App.java"));
        assertTrue(impact.modules().contains("ui"));
        assertTrue(impact.capabilities().contains("login"));
        assertTrue(impact.testSuiteCategories().contains("E2E"));
    }

    @Test
    void ver18unmatchedDiffYieldsAnEmptyImpactSetNotASilentPass() {
        var impact = service.impactSet("pilot", List.of("docs/README.md"), 7, null, 50);
        assertNotNull(impact.baselineCommit());
        assertTrue(impact.testSuiteCategories().isEmpty());
    }

    @Test
    void ver19llmEdgesMayOnlyExpandTheClosure() {
        VerificationNode file = nodeStore.values().stream()
                .filter(node -> node.getPath().equals("src/App.java")).findFirst().orElseThrow();
        VerificationNode extraSuite = save("pilot", Type.TEST_SUITE, "tests/smoke.spec.ts", "SMOKE", "base-1");
        link("pilot", file, extraSuite, VerificationProvenance.LLM);

        var impact = service.impactSet("pilot", List.of("src/App.java"), 7, null, 50);
        assertTrue(impact.testSuiteCategories().contains("SMOKE"));
        assertEquals(1, impact.appliedLlmEdges().size());
        // The pre-existing closure is unchanged: an LLM edge cannot remove STATIC/HUMAN reachability.
        assertTrue(impact.testSuiteCategories().contains("E2E"));
    }

    @Test
    void ver20missingGraphFallsBackWithAReason() {
        var impact = service.impactSet("unknown-project", List.of("src/App.java"), 7, null, 50);
        assertEquals("GRAPH_MISSING", impact.fallbackReason());
        assertTrue(impact.testSuiteCategories().isEmpty());
    }

    @Test
    void ver20staleBaselineByAgeFallsBack() {
        nodeStore.values().forEach(node -> forceCapturedAt(node, OffsetDateTime.now().minusDays(30)));
        var impact = service.impactSet("pilot", List.of("src/App.java"), 7, null, 50);
        assertEquals("GRAPH_BASELINE_EXPIRED_AGE", impact.fallbackReason());
    }

    @Test
    void ver20mergeLagFallsBack() {
        var impact = service.impactSet("pilot", List.of("src/App.java"), 7, 51, 50);
        assertEquals("GRAPH_BASELINE_EXPIRED_MERGE_LAG", impact.fallbackReason());
    }

    private VerificationNode save(String project, Type type, String path, String name, String baseline) {
        VerificationNode node = VerificationNode.capture(project, type, path, name, baseline);
        nodeStore.put(node.getId(), node);
        return node;
    }

    private void link(String project, VerificationNode from, VerificationNode to, VerificationProvenance provenance) {
        VerificationEdge edge = VerificationEdge.link(project, from.getId(), to.getId(), provenance);
        edgeStore.put(edge.getId(), edge);
    }

    private static void forceCapturedAt(VerificationNode node, OffsetDateTime capturedAt) {
        try {
            Field field = VerificationNode.class.getDeclaredField("capturedAt");
            field.setAccessible(true);
            field.set(node, capturedAt);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
