/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Missing-test parity for handoff route graph, transfer state, completion future, and session resume behavior.
 *
 * <p>Mirrors Python's tests in
 * {@code tests/unit_tests/multi_agent/builtin_teams/handoff/test_handoff_orchestrator.py}.</p>
 */
class HandoffOrchestratorMissingTest {

    @Test
    void fullMeshNoRoutes() {
        Map<String, Set<String>> graph = HandoffOrchestrator.buildRouteGraph(List.of("a", "b", "c"), List.of());

        assertEquals(Set.of("b", "c"), graph.get("a"));
        assertEquals(Set.of("a", "c"), graph.get("b"));
        assertEquals(Set.of("a", "b"), graph.get("c"));
    }

    @Test
    void fullMeshHasNoSelfLoops() {
        Map<String, Set<String>> graph = HandoffOrchestrator.buildRouteGraph(List.of("a", "b"), List.of());

        assertFalse(graph.get("a").contains("a"));
        assertFalse(graph.get("b").contains("b"));
    }

    @Test
    void explicitRoutesAreRespected() {
        List<HandoffRoute> routes = List.of(new HandoffRoute("a", "b"), new HandoffRoute("b", "c"));

        Map<String, Set<String>> graph = HandoffOrchestrator.buildRouteGraph(List.of("a", "b", "c"), routes);

        assertEquals(Set.of("b"), graph.get("a"));
        assertEquals(Set.of("c"), graph.get("b"));
        assertEquals(Set.of(), graph.get("c"));
    }

    @Test
    void explicitRoutesNonSourceHasEmptySet() {
        Map<String, Set<String>> graph = HandoffOrchestrator.buildRouteGraph(
                List.of("a", "b", "c"),
                List.of(new HandoffRoute("a", "b"))
        );

        assertEquals(Set.of(), graph.get("b"));
        assertEquals(Set.of(), graph.get("c"));
    }

    @Test
    void singleAgentHasEmptyTargets() {
        Map<String, Set<String>> graph = HandoffOrchestrator.buildRouteGraph(List.of("a"), List.of());

        assertEquals(Set.of(), graph.get("a"));
    }

    @Test
    void emptyAgentsProduceEmptyGraph() {
        assertEquals(Map.of(), HandoffOrchestrator.buildRouteGraph(List.of(), List.of()));
    }

    @Test
    void multipleRoutesFromSameSourceAreCollected() {
        List<HandoffRoute> routes = List.of(new HandoffRoute("a", "b"), new HandoffRoute("a", "c"));

        Map<String, Set<String>> graph = HandoffOrchestrator.buildRouteGraph(List.of("a", "b", "c"), routes);

        assertEquals(Set.of("b", "c"), graph.get("a"));
    }

    @Test
    void buildRouteGraphIsStaticMethod() throws Exception {
        Method method = HandoffOrchestrator.class.getDeclaredMethod("buildRouteGraph", List.class, List.class);

        assertTrue(Modifier.isStatic(method.getModifiers()));
    }

    @Test
    void initialHandoffCountIsZero() {
        assertEquals(0, makeCoord().getHandoffCount());
    }

    @Test
    void initialCurrentAgentIdIsStartAgent() {
        assertEquals("a", makeCoord("a", null, null, 10, null).getCurrentAgentId());
    }

    @Test
    void noConfigUsesDefaultMaxHandoffs() {
        HandoffOrchestrator coordinator = new HandoffOrchestrator("a", List.of("a", "b"));

        assertEquals(10, coordinator.getMaxHandoffs());
    }

    @Test
    void configMaxHandoffsIsApplied() {
        assertEquals(3, makeCoord("a", null, null, 3, null).getMaxHandoffs());
    }

    @Test
    void doneFutureInitiallyNull() {
        assertNull(privateField(makeCoord(), "doneFuture"));
    }

    @Test
    void approvesValidFullMeshRoute() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b"), null, 10, null);

        assertTrue(coordinator.requestHandoff("b").join());
    }

    @Test
    void incrementsHandoffCountOnApproval() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b"), null, 10, null);

        coordinator.requestHandoff("b").join();

        assertEquals(1, coordinator.getHandoffCount());
    }

    @Test
    void updatesCurrentAgentIdOnApproval() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b"), null, 10, null);

        coordinator.requestHandoff("b").join();

        assertEquals("b", coordinator.getCurrentAgentId());
    }

    @Test
    void rejectsWhenMaxHandoffsZero() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b"), null, 0, null);

        assertFalse(coordinator.requestHandoff("b").join());
    }

    @Test
    void rejectsWhenMaxHandoffsReached() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b", "c"), null, 1, null);

        coordinator.requestHandoff("b").join();

        assertFalse(coordinator.requestHandoff("c").join());
    }

    @Test
    void rejectionDoesNotIncrementCount() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b"), null, 0, null);

        coordinator.requestHandoff("b").join();

        assertEquals(0, coordinator.getHandoffCount());
    }

    @Test
    void rejectionDoesNotChangeCurrentAgentId() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b"), null, 0, null);

        coordinator.requestHandoff("b").join();

        assertEquals("a", coordinator.getCurrentAgentId());
    }

    @Test
    void rejectsInvalidExplicitRoute() {
        HandoffOrchestrator coordinator = makeCoord(
                "a",
                List.of("a", "b", "c"),
                List.of(new HandoffRoute("a", "b")),
                10,
                null
        );

        assertFalse(coordinator.requestHandoff("c").join());
    }

    @Test
    void approvesValidExplicitRoute() {
        HandoffOrchestrator coordinator = makeCoord(
                "a",
                List.of("a", "b"),
                List.of(new HandoffRoute("a", "b")),
                10,
                null
        );

        assertTrue(coordinator.requestHandoff("b").join());
    }

    @Test
    void rejectsWhenTerminationConditionTrue() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b"), null, 10, ignored -> true);

        assertFalse(coordinator.requestHandoff("b").join());
    }

    @Test
    void approvesWhenTerminationConditionFalse() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b"), null, 10, ignored -> false);

        assertTrue(coordinator.requestHandoff("b").join());
    }

    @Test
    void requestHandoffFutureRejectsWhenTerminationTrue() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b"), null, 10, ignored -> true);

        CompletableFuture<Boolean> result = coordinator.requestHandoff("b");

        assertTrue(result.isDone());
        assertFalse(result.join());
    }

    @Test
    void requestHandoffFutureApprovesWhenTerminationFalse() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b"), null, 10, ignored -> false);

        CompletableFuture<Boolean> result = coordinator.requestHandoff("b");

        assertTrue(result.isDone());
        assertTrue(result.join());
    }

    @Test
    void chainedHandoffsTrackCount() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b", "c"), null, 5, null);

        coordinator.requestHandoff("b").join();
        coordinator.requestHandoff("c").join();

        assertEquals(2, coordinator.getHandoffCount());
    }

    @Test
    void chainedHandoffsTrackCurrentAgent() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b", "c"), null, 5, null);

        coordinator.requestHandoff("b").join();
        coordinator.requestHandoff("c").join();

        assertEquals("c", coordinator.getCurrentAgentId());
    }

    @Test
    void completeResolvesFuture() {
        HandoffOrchestrator coordinator = makeCoord();

        coordinator.complete(Map.of("answer", 42)).join();

        assertEquals(Map.of("answer", 42), coordinator.doneFuture().join());
    }

    @Test
    void completeWithNullResult() {
        HandoffOrchestrator coordinator = makeCoord();

        coordinator.complete(null).join();

        assertNull(coordinator.doneFuture().join());
    }

    @Test
    void completeIsIdempotentAndFirstWins() {
        HandoffOrchestrator coordinator = makeCoord();

        coordinator.complete("first").join();
        coordinator.complete("second").join();

        assertEquals("first", coordinator.doneFuture().join());
    }

    @Test
    void errorRejectsFuture() {
        HandoffOrchestrator coordinator = makeCoord();

        coordinator.error(new IllegalArgumentException("boom")).join();

        CompletionException exception = assertThrows(CompletionException.class, () -> coordinator.doneFuture().join());
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals("boom", exception.getCause().getMessage());
    }

    @Test
    void errorIsIdempotentAndFirstExceptionWins() {
        HandoffOrchestrator coordinator = makeCoord();

        coordinator.error(new IllegalArgumentException("first")).join();
        coordinator.error(new IllegalStateException("second")).join();

        CompletionException exception = assertThrows(CompletionException.class, () -> coordinator.doneFuture().join());
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals("first", exception.getCause().getMessage());
    }

    @Test
    void doneFutureIsDoneAfterComplete() {
        HandoffOrchestrator coordinator = makeCoord();

        coordinator.complete("ok").join();

        assertTrue(coordinator.doneFuture().isDone());
    }

    @Test
    void doneFutureIsDoneAfterError() {
        HandoffOrchestrator coordinator = makeCoord();

        coordinator.error(new RuntimeException("err")).join();

        assertTrue(coordinator.doneFuture().isDone());
    }

    @Test
    void doneFutureIsCompletableFuture() {
        assertInstanceOf(CompletableFuture.class, makeCoord().doneFuture());
    }

    @Test
    void doneFutureIsCached() {
        HandoffOrchestrator coordinator = makeCoord();

        assertSame(coordinator.doneFuture(), coordinator.doneFuture());
    }

    @Test
    void doneFutureIsNotDoneInitially() {
        assertFalse(makeCoord().doneFuture().isDone());
    }

    @Test
    void handoffCountPropertyIsZeroInitially() {
        assertEquals(0, makeCoord().getHandoffCount());
    }

    @Test
    void currentAgentIdReflectsStart() {
        assertEquals("x", makeCoord("x", List.of("x", "y"), null, 10, null).getCurrentAgentId());
    }

    @Test
    void handoffCountPropertyIncrements() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b"), null, 10, null);

        coordinator.requestHandoff("b").join();

        assertEquals(1, coordinator.getHandoffCount());
    }

    @Test
    void currentAgentIdPropertyUpdates() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b"), null, 10, null);

        coordinator.requestHandoff("b").join();

        assertEquals("b", coordinator.getCurrentAgentId());
    }

    @Test
    void saveCallsUpdateState() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b"), null, 10, null);
        coordinator.requestHandoff("b").join();
        InMemorySession session = makeSession(null);

        coordinator.saveToSession(session);

        assertEquals(1, session.updateStateCount);
    }

    @Test
    void savePersistsCurrentAgent() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b"), null, 10, null);
        coordinator.requestHandoff("b").join();
        InMemorySession session = makeSession(null);

        coordinator.saveToSession(session);

        assertEquals("b", savedCoordinatorState(session).get("current_agent_id"));
    }

    @Test
    void savePersistsHandoffCount() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b"), null, 10, null);
        coordinator.requestHandoff("b").join();
        InMemorySession session = makeSession(null);

        coordinator.saveToSession(session);

        assertEquals(1, savedCoordinatorState(session).get("handoff_count"));
    }

    @Test
    void restoreWithSnapshot() {
        HandoffOrchestrator restored = HandoffOrchestrator.restoreFromSession(
                makeSession(Map.of("current_agent_id", "b", "handoff_count", 3)),
                "a",
                List.of("a", "b"),
                null
        );

        assertEquals("b", restored.getCurrentAgentId());
        assertEquals(3, restored.getHandoffCount());
    }

    @Test
    void restoreWithoutSnapshotUsesStart() {
        HandoffOrchestrator restored = HandoffOrchestrator.restoreFromSession(
                makeSession(null),
                "a",
                List.of("a", "b"),
                null
        );

        assertEquals("a", restored.getCurrentAgentId());
        assertEquals(0, restored.getHandoffCount());
    }

    @Test
    void restoreFromSessionIsStaticFactory() throws Exception {
        Method method = HandoffOrchestrator.class.getDeclaredMethod(
                "restoreFromSession",
                HandoffOrchestrator.SessionStatePort.class,
                String.class,
                List.class,
                HandoffConfig.class
        );

        assertTrue(Modifier.isStatic(method.getModifiers()));
    }

    @Test
    void restoreReturnsOrchestratorInstance() {
        HandoffOrchestrator restored = HandoffOrchestrator.restoreFromSession(
                makeSession(null),
                "a",
                List.of("a", "b"),
                null
        );

        assertNotNull(restored);
        assertInstanceOf(HandoffOrchestrator.class, restored);
    }

    @Test
    void saveRestoreRoundTrip() {
        HandoffOrchestrator coordinator = makeCoord("a", List.of("a", "b", "c"), null, 5, null);
        coordinator.requestHandoff("b").join();
        coordinator.requestHandoff("c").join();
        InMemorySession session = makeSession(null);

        coordinator.saveToSession(session);
        HandoffOrchestrator restored = HandoffOrchestrator.restoreFromSession(
                session,
                "a",
                List.of("a", "b", "c"),
                null
        );

        assertEquals("c", restored.getCurrentAgentId());
        assertEquals(2, restored.getHandoffCount());
    }

    private static HandoffOrchestrator makeCoord() {
        return makeCoord("a", null, null, 10, null);
    }

    private static HandoffOrchestrator makeCoord(
            String start,
            List<String> agents,
            List<HandoffRoute> routes,
            int maxHandoffs,
            HandoffTerminationCondition terminationCondition) {
        List<String> effectiveAgents = agents == null ? List.of("a", "b", "c") : agents;
        HandoffConfig config = new HandoffConfig();
        config.setMaxHandoffs(maxHandoffs);
        config.setRoutes(routes == null ? List.of() : routes);
        config.setTerminationCondition(terminationCondition);
        return new HandoffOrchestrator(start, effectiveAgents, config);
    }

    private static InMemorySession makeSession(Map<String, Object> snapshot) {
        InMemorySession session = new InMemorySession();
        session.snapshot = snapshot;
        return session;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> savedCoordinatorState(InMemorySession session) {
        return (Map<String, Object>) session.state.get(HandoffOrchestrator.COORDINATOR_STATE_KEY);
    }

    private static Object privateField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * Mirrors Python's {@code _make_session} helper in
     * {@code tests/unit_tests/multi_agent/builtin_teams/handoff/test_handoff_orchestrator.py}.
     */
    private static final class InMemorySession implements HandoffOrchestrator.SessionStatePort {
        private final Map<String, Object> state = new LinkedHashMap<>();
        private Map<String, Object> snapshot;
        private int updateStateCount;

        @Override
        public Object getState(String key) {
            if (HandoffOrchestrator.COORDINATOR_STATE_KEY.equals(key)) {
                return snapshot == null ? state.get(key) : snapshot;
            }
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> update) {
            updateStateCount += 1;
            state.putAll(update);
        }
    }
}
