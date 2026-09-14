package com.company.forgeops.v2.workflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FeedbackStateTest {

    private static final Map<FeedbackState, Set<FeedbackState>> ALLOWED = Map.ofEntries(
            Map.entry(FeedbackState.RECEIVED, EnumSet.of(FeedbackState.CONTEXT_READY)),
            Map.entry(FeedbackState.CONTEXT_READY, EnumSet.of(FeedbackState.TRIAGE_QUEUED)),
            Map.entry(FeedbackState.TRIAGE_QUEUED, EnumSet.of(FeedbackState.TRIAGE_RUNNING)),
            Map.entry(FeedbackState.TRIAGE_RUNNING, EnumSet.of(FeedbackState.NEEDS_INPUT,
                    FeedbackState.NO_CODE_REQUIRED, FeedbackState.TRIAGE_FAILED, FeedbackState.CODE_QUEUED)),
            Map.entry(FeedbackState.NEEDS_INPUT, EnumSet.of(FeedbackState.CONTEXT_READY, FeedbackState.TRIAGE_QUEUED)),
            Map.entry(FeedbackState.NO_CODE_REQUIRED, EnumSet.of(FeedbackState.WAITING_VERIFY, FeedbackState.REOPENED)),
            Map.entry(FeedbackState.TRIAGE_FAILED, EnumSet.of(FeedbackState.TRIAGE_QUEUED)),
            Map.entry(FeedbackState.CODE_QUEUED, EnumSet.of(FeedbackState.CODE_RUNNING)),
            Map.entry(FeedbackState.CODE_RUNNING, EnumSet.of(FeedbackState.EXECUTION_FAILED, FeedbackState.PR_READY)),
            Map.entry(FeedbackState.EXECUTION_FAILED, EnumSet.of(FeedbackState.CODE_QUEUED)),
            Map.entry(FeedbackState.PR_READY, EnumSet.of(FeedbackState.BUILD_RUNNING)),
            Map.entry(FeedbackState.BUILD_RUNNING, EnumSet.of(FeedbackState.BUILD_FAILED, FeedbackState.DEPLOY_RUNNING)),
            Map.entry(FeedbackState.BUILD_FAILED, EnumSet.of(FeedbackState.BUILD_RUNNING)),
            Map.entry(FeedbackState.DEPLOY_RUNNING, EnumSet.of(FeedbackState.DEPLOY_FAILED, FeedbackState.WAITING_VERIFY)),
            Map.entry(FeedbackState.DEPLOY_FAILED, EnumSet.of(FeedbackState.DEPLOY_RUNNING)),
            Map.entry(FeedbackState.WAITING_VERIFY, EnumSet.of(FeedbackState.DONE, FeedbackState.REOPENED)),
            Map.entry(FeedbackState.DONE, EnumSet.of(FeedbackState.REOPENED)),
            Map.entry(FeedbackState.REOPENED, EnumSet.of(FeedbackState.CONTEXT_READY)));

    static Stream<Arguments> everyTransition() {
        return Stream.of(FeedbackState.values())
                .flatMap(from -> Stream.of(FeedbackState.values())
                        .map(to -> Arguments.of(from, to, ALLOWED.getOrDefault(from, Set.of()).contains(to))));
    }

    @ParameterizedTest
    @MethodSource("everyTransition")
    void stateMachineMatchesTheV2Contract(FeedbackState from, FeedbackState to, boolean expected) {
        if (expected) {
            assertTrue(from.canTransitionTo(to), () -> from + " -> " + to + " must be allowed");
        } else {
            assertFalse(from.canTransitionTo(to), () -> from + " -> " + to + " must be rejected");
        }
    }
}
