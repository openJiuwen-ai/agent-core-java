/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.builtin_teams.handoff;

import com.openjiuwen.core.multiagent.teams.handoff.HandoffConfig;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffOrchestrator;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffRoute;
import com.openjiuwen.core.session.Session;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for handoff orchestrator.
 *
 * <p>Mirrors Python's {@code test_handoff_orchestrator.py} in
 * {@code tests.unit_tests.multi_agent.builtin_teams.handoff}.
 */
class TestHandoffOrchestrator {

    private static final class FakeSession implements Session {
        private final Map<String, Object> state = new HashMap<>();
        private int updateCalls;

        FakeSession() {
        }

        FakeSession(Map<String, Object> snapshot) {
            if (snapshot != null) {
                state.put(HandoffOrchestrator.COORDINATOR_STATE_KEY, snapshot);
            }
        }

        @Override
        public String getSessionId() {
            return "sid";
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> state) {
            updateCalls++;
            this.state.putAll(state);
        }

        int getUpdateCalls() {
            return updateCalls;
        }
    }

    private static HandoffOrchestrator coord() {
        return coord("a", List.of("a", "b"), 10, List.of(), null);
    }

    private static HandoffOrchestrator coord(String start, List<String> agents) {
        return coord(start, agents, 10, List.of(), null);
    }

    private static HandoffOrchestrator coord(
            String start,
            List<String> agents,
            int maxHandoffs,
            List<HandoffRoute> routes,
            java.util.function.Function<Object, Object> terminationCondition) {
        HandoffConfig config = HandoffConfig.builder()
                .maxHandoffs(maxHandoffs)
                .routes(routes)
                .terminationCondition(terminationCondition)
                .build();
        return new HandoffOrchestrator(start, agents, config);
    }

    @Nested
    class TestBuildRouteGraph {
        @Test
        void testFullMeshNoRoutes() {
            Map<String, Set<String>> graph = HandoffOrchestrator.buildRouteGraph(List.of("a", "b", "c"), List.of());
            assertEquals(Set.of("b", "c"), graph.get("a"));
            assertEquals(Set.of("a", "c"), graph.get("b"));
            assertEquals(Set.of("a", "b"), graph.get("c"));
        }

        @Test
        void testFullMeshNoSelfLoops() {
            Map<String, Set<String>> graph = HandoffOrchestrator.buildRouteGraph(List.of("a", "b"), List.of());
            assertFalse(graph.get("a").contains("a"));
            assertFalse(graph.get("b").contains("b"));
        }

        @Test
        void testExplicitRoutesRespected() {
            Map<String, Set<String>> graph = HandoffOrchestrator.buildRouteGraph(
                    List.of("a", "b", "c"), List.of(new HandoffRoute("a", "b"), new HandoffRoute("b", "c")));
            assertEquals(Set.of("b"), graph.get("a"));
            assertEquals(Set.of("c"), graph.get("b"));
            assertEquals(Set.of(), graph.get("c"));
        }

        @Test
        void testExplicitRoutesNonSourceHasEmptySet() {
            Map<String, Set<String>> graph = HandoffOrchestrator.buildRouteGraph(
                    List.of("a", "b", "c"), List.of(new HandoffRoute("a", "b")));
            assertEquals(Set.of(), graph.get("b"));
            assertEquals(Set.of(), graph.get("c"));
        }

        @Test
        void testSingleAgentEmptyTargets() {
            assertEquals(Set.of(), HandoffOrchestrator.buildRouteGraph(List.of("a"), List.of()).get("a"));
        }

        @Test
        void testEmptyAgentsEmptyGraph() {
            assertEquals(Map.of(), HandoffOrchestrator.buildRouteGraph(List.of(), List.of()));
        }

        @Test
        void testMultipleRoutesFromSameSource() {
            Map<String, Set<String>> graph = HandoffOrchestrator.buildRouteGraph(
                    List.of("a", "b", "c"), List.of(new HandoffRoute("a", "b"), new HandoffRoute("a", "c")));
            assertEquals(Set.of("b", "c"), graph.get("a"));
        }

        @Test
        void testIsStaticMethod() throws NoSuchMethodException {
            assertTrue(Modifier.isStatic(HandoffOrchestrator.class
                    .getMethod("buildRouteGraph", List.class, List.class)
                    .getModifiers()));
        }
    }

    @Nested
    class TestHandoffOrchestratorInit {
        @Test
        void testInitialHandoffCountZero() {
            assertEquals(0, coord().getHandoffCount());
        }

        @Test
        void testInitialCurrentAgentId() {
            assertEquals("a", coord("a", List.of("a", "b")).getCurrentAgentId());
        }

        @Test
        void testNoConfigUsesDefaultMaxHandoffs() {
            assertEquals(10, new HandoffOrchestrator("a", List.of("a", "b"), null).getMaxHandoffs());
        }

        @Test
        void testConfigMaxHandoffsApplied() {
            assertEquals(3, coord("a", List.of("a", "b"), 3, List.of(), null).getMaxHandoffs());
        }

        @Test
        void testDoneFutureInitiallyNone() throws ReflectiveOperationException {
            Field field = HandoffOrchestrator.class.getDeclaredField("doneFuture");
            field.setAccessible(true);
            assertNull(field.get(coord()));
        }
    }

    @Nested
    class TestRequestHandoff {
        @Test
        void testApprovesValidFullMeshRoute() {
            assertTrue(coord("a", List.of("a", "b")).requestHandoff("b"));
        }

        @Test
        void testIncrementsHandoffCountOnApproval() {
            HandoffOrchestrator coord = coord("a", List.of("a", "b"));
            coord.requestHandoff("b");
            assertEquals(1, coord.getHandoffCount());
        }

        @Test
        void testUpdatesCurrentAgentIdOnApproval() {
            HandoffOrchestrator coord = coord("a", List.of("a", "b"));
            coord.requestHandoff("b");
            assertEquals("b", coord.getCurrentAgentId());
        }

        @Test
        void testRejectsWhenMaxHandoffsZero() {
            assertFalse(coord("a", List.of("a", "b"), 0, List.of(), null).requestHandoff("b"));
        }

        @Test
        void testRejectsWhenMaxHandoffsReached() {
            HandoffOrchestrator coord = coord("a", List.of("a", "b", "c"), 1, List.of(), null);
            coord.requestHandoff("b");
            assertFalse(coord.requestHandoff("c"));
        }

        @Test
        void testNoCountIncrementOnRejection() {
            HandoffOrchestrator coord = coord("a", List.of("a", "b"), 0, List.of(), null);
            coord.requestHandoff("b");
            assertEquals(0, coord.getHandoffCount());
        }

        @Test
        void testNoAgentIdChangeOnRejection() {
            HandoffOrchestrator coord = coord("a", List.of("a", "b"), 0, List.of(), null);
            coord.requestHandoff("b");
            assertEquals("a", coord.getCurrentAgentId());
        }

        @Test
        void testRejectsInvalidExplicitRoute() {
            assertFalse(coord("a", List.of("a", "b", "c"), 10, List.of(new HandoffRoute("a", "b")), null)
                    .requestHandoff("c"));
        }

        @Test
        void testApprovesValidExplicitRoute() {
            assertTrue(coord("a", List.of("a", "b"), 10, List.of(new HandoffRoute("a", "b")), null)
                    .requestHandoff("b"));
        }

        @Test
        void testRejectsWhenSyncTerminationTrue() {
            assertFalse(coord("a", List.of("a", "b"), 10, List.of(), ignored -> true).requestHandoff("b"));
        }

        @Test
        void testApprovesWhenSyncTerminationFalse() {
            assertTrue(coord("a", List.of("a", "b"), 10, List.of(), ignored -> false).requestHandoff("b"));
        }

        @Test
        void testRejectsWhenAsyncTerminationTrue() {
            assertFalse(coord("a", List.of("a", "b"), 10, List.of(),
                    ignored -> CompletableFuture.completedFuture(true)).requestHandoff("b"));
        }

        @Test
        void testApprovesWhenAsyncTerminationFalse() {
            assertTrue(coord("a", List.of("a", "b"), 10, List.of(),
                    ignored -> CompletableFuture.completedFuture(false)).requestHandoff("b"));
        }

        @Test
        void testChainedHandoffsTrackCount() {
            HandoffOrchestrator coord = coord("a", List.of("a", "b", "c"), 5, List.of(), null);
            coord.requestHandoff("b");
            coord.requestHandoff("c");
            assertEquals(2, coord.getHandoffCount());
        }

        @Test
        void testChainedHandoffsTrackCurrentAgent() {
            HandoffOrchestrator coord = coord("a", List.of("a", "b", "c"), 5, List.of(), null);
            coord.requestHandoff("b");
            coord.requestHandoff("c");
            assertEquals("c", coord.getCurrentAgentId());
        }
    }

    @Nested
    class TestCompleteAndError {
        @Test
        void testCompleteResolvesFuture() {
            HandoffOrchestrator coord = coord();
            coord.complete(Map.of("answer", 42));
            assertEquals(Map.of("answer", 42), coord.getDoneFuture().join());
        }

        @Test
        void testCompleteWithNoneResult() {
            HandoffOrchestrator coord = coord();
            coord.complete(null);
            assertNull(coord.getDoneFuture().join());
        }

        @Test
        void testCompleteIdempotentFirstWins() {
            HandoffOrchestrator coord = coord();
            coord.complete("first");
            coord.complete("second");
            assertEquals("first", coord.getDoneFuture().join());
        }

        @Test
        void testErrorRejectsFuture() {
            HandoffOrchestrator coord = coord();
            coord.error(new IllegalArgumentException("boom"));
            CompletionException error = assertThrows(CompletionException.class, () -> coord.getDoneFuture().join());
            assertEquals("boom", error.getCause().getMessage());
        }

        @Test
        void testErrorIdempotentFirstExceptionWins() {
            HandoffOrchestrator coord = coord();
            coord.error(new IllegalArgumentException("first"));
            coord.error(new IllegalStateException("second"));
            CompletionException error = assertThrows(CompletionException.class, () -> coord.getDoneFuture().join());
            assertEquals("first", error.getCause().getMessage());
        }

        @Test
        void testDoneFutureDoneAfterComplete() {
            HandoffOrchestrator coord = coord();
            coord.complete("ok");
            assertTrue(coord.getDoneFuture().isDone());
        }

        @Test
        void testDoneFutureDoneAfterError() {
            HandoffOrchestrator coord = coord();
            coord.error(new RuntimeException("err"));
            assertTrue(coord.getDoneFuture().isDone());
        }
    }

    @Nested
    class TestDoneFuture {
        @Test
        void testDoneFutureIsCompletableFuture() {
            assertInstanceOf(CompletableFuture.class, coord().getDoneFuture());
        }

        @Test
        void testDoneFutureCached() {
            HandoffOrchestrator coord = coord();
            assertSame(coord.getDoneFuture(), coord.getDoneFuture());
        }

        @Test
        void testDoneFutureNotDoneInitially() {
            assertFalse(coord().getDoneFuture().isDone());
        }
    }

    @Nested
    class TestProperties {
        @Test
        void testHandoffCountZeroInitially() {
            assertEquals(0, coord().getHandoffCount());
        }

        @Test
        void testCurrentAgentIdReflectsStart() {
            assertEquals("x", coord("x", List.of("x", "y")).getCurrentAgentId());
        }

        @Test
        void testHandoffCountIncrements() {
            HandoffOrchestrator coord = coord("a", List.of("a", "b"));
            coord.requestHandoff("b");
            assertEquals(1, coord.getHandoffCount());
        }

        @Test
        void testCurrentAgentIdUpdates() {
            HandoffOrchestrator coord = coord("a", List.of("a", "b"));
            coord.requestHandoff("b");
            assertEquals("b", coord.getCurrentAgentId());
        }
    }

    @Nested
    class TestSaveRestoreSession {
        @Test
        void testSaveCallsUpdateState() {
            HandoffOrchestrator coord = coord("a", List.of("a", "b"));
            coord.requestHandoff("b");
            FakeSession session = new FakeSession();
            coord.saveToSession(session);
            assertEquals(1, session.getUpdateCalls());
        }

        @Test
        @SuppressWarnings("unchecked")
        void testSavePersistsCurrentAgent() {
            HandoffOrchestrator coord = coord("a", List.of("a", "b"));
            coord.requestHandoff("b");
            FakeSession session = new FakeSession();
            coord.saveToSession(session);
            Map<String, Object> saved = (Map<String, Object>) session.getState(HandoffOrchestrator.COORDINATOR_STATE_KEY);
            assertEquals("b", saved.get("current_agent_id"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void testSavePersistsHandoffCount() {
            HandoffOrchestrator coord = coord("a", List.of("a", "b"));
            coord.requestHandoff("b");
            FakeSession session = new FakeSession();
            coord.saveToSession(session);
            Map<String, Object> saved = (Map<String, Object>) session.getState(HandoffOrchestrator.COORDINATOR_STATE_KEY);
            assertEquals(1, saved.get("handoff_count"));
        }

        @Test
        void testRestoreWithSnapshot() {
            HandoffOrchestrator restored = HandoffOrchestrator.restoreFromSession(
                    new FakeSession(Map.of("current_agent_id", "b", "handoff_count", 3)),
                    "a",
                    List.of("a", "b"));
            assertEquals("b", restored.getCurrentAgentId());
            assertEquals(3, restored.getHandoffCount());
        }

        @Test
        void testRestoreWithoutSnapshotUsesStart() {
            HandoffOrchestrator restored = HandoffOrchestrator.restoreFromSession(
                    new FakeSession(),
                    "a",
                    List.of("a", "b"));
            assertEquals("a", restored.getCurrentAgentId());
            assertEquals(0, restored.getHandoffCount());
        }

        @Test
        void testRestoreIsStaticMethod() throws NoSuchMethodException {
            assertTrue(Modifier.isStatic(HandoffOrchestrator.class
                    .getMethod("restoreFromSession", Session.class, String.class, List.class)
                    .getModifiers()));
        }

        @Test
        void testRestoreReturnsOrchestratorInstance() {
            assertInstanceOf(HandoffOrchestrator.class, HandoffOrchestrator.restoreFromSession(
                    new FakeSession(), "a", List.of("a", "b")));
        }

        @Test
        void testSaveRestoreRoundTrip() {
            HandoffOrchestrator coord = coord("a", List.of("a", "b", "c"), 5, List.of(), null);
            coord.requestHandoff("b");
            coord.requestHandoff("c");
            FakeSession session = new FakeSession();
            coord.saveToSession(session);
            HandoffOrchestrator restored = HandoffOrchestrator.restoreFromSession(
                    session, "a", List.of("a", "b", "c"));
            assertEquals("c", restored.getCurrentAgentId());
            assertEquals(2, restored.getHandoffCount());
        }
    }
}
