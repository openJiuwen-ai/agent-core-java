/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for the handoff runtime coordinator.
 *
 * <p>Mirrors Python's {@code HandoffOrchestrator} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_orchestrator.py}.</p>
 */
class HandoffOrchestratorTest {

    @Test
    void buildRouteGraphUsesFullMeshWhenRoutesAreEmpty() {
        Map<String, Set<String>> graph = HandoffOrchestrator.buildRouteGraph(
                List.of("a", "b", "c"),
                List.of()
        );

        assertThat(graph).containsEntry("a", Set.of("b", "c"));
        assertThat(graph).containsEntry("b", Set.of("a", "c"));
        assertThat(graph).containsEntry("c", Set.of("a", "b"));
    }

    @Test
    void buildRouteGraphUsesExplicitRoutesAndKeepsUnknownSources() {
        Map<String, Set<String>> graph = HandoffOrchestrator.buildRouteGraph(
                List.of("a", "b"),
                List.of(new HandoffRoute("a", "b"), new HandoffRoute("external", "a"))
        );

        assertThat(graph).containsEntry("a", Set.of("b"));
        assertThat(graph).containsEntry("b", Set.of());
        assertThat(graph).containsEntry("external", Set.of("a"));
    }

    @Test
    void requestHandoffApprovesAllowedRouteAndUpdatesState() {
        HandoffConfig config = new HandoffConfig();
        config.setMaxHandoffs(2);
        config.setRoutes(List.of(new HandoffRoute("a", "b"), new HandoffRoute("b", "c")));
        HandoffOrchestrator coordinator = new HandoffOrchestrator("a", List.of("a", "b", "c"), config);

        assertThat(coordinator.requestHandoff("b", "first").join()).isTrue();

        assertThat(coordinator.getCurrentAgentId()).isEqualTo("b");
        assertThat(coordinator.getHandoffCount()).isEqualTo(1);
    }

    @Test
    void requestHandoffRejectsDisallowedRouteMaxLimitAndTermination() {
        HandoffConfig routeConfig = new HandoffConfig();
        routeConfig.setMaxHandoffs(1);
        routeConfig.setRoutes(List.of(new HandoffRoute("a", "b")));
        HandoffOrchestrator routeCoordinator = new HandoffOrchestrator("a", List.of("a", "b", "c"), routeConfig);

        assertThat(routeCoordinator.requestHandoff("c").join()).isFalse();
        assertThat(routeCoordinator.requestHandoff("b").join()).isTrue();
        assertThat(routeCoordinator.requestHandoff("a").join()).isFalse();

        HandoffConfig terminationConfig = new HandoffConfig();
        terminationConfig.setTerminationCondition(orchestrator -> true);
        HandoffOrchestrator terminated = new HandoffOrchestrator("a", List.of("a", "b"), terminationConfig);

        assertThat(terminated.requestHandoff("b").join()).isFalse();
        assertThat(terminated.getCurrentAgentId()).isEqualTo("a");
        assertThat(terminated.getHandoffCount()).isZero();
    }

    @Test
    void completeAndErrorResolveLazyDoneFutureOnce() {
        HandoffOrchestrator coordinator = new HandoffOrchestrator("a", List.of("a", "b"));

        coordinator.complete("done").join();
        coordinator.error(new IllegalStateException("late")).join();

        assertThat(coordinator.doneFuture().join()).isEqualTo("done");

        HandoffOrchestrator failing = new HandoffOrchestrator("a", List.of("a", "b"));
        failing.error(new IllegalArgumentException("boom")).join();

        assertThatThrownBy(() -> failing.doneFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveAndRestoreSessionState() {
        HandoffOrchestrator coordinator = new HandoffOrchestrator("a", List.of("a", "b", "c"));
        coordinator.requestHandoff("b").join();
        InMemorySession session = new InMemorySession();

        coordinator.saveToSession(session);
        HandoffOrchestrator restored = HandoffOrchestrator.restoreFromSession(
                session,
                "a",
                List.of("a", "b", "c"),
                null
        );

        assertThat(restored.getCurrentAgentId()).isEqualTo("b");
        assertThat(restored.getHandoffCount()).isEqualTo(1);
    }

    private static final class InMemorySession implements HandoffOrchestrator.SessionStatePort {
        private final Map<String, Object> state = new LinkedHashMap<>();

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> update) {
            state.putAll(update);
        }
    }
}
