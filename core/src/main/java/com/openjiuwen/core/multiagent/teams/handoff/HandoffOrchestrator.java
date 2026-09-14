/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.session.BaseSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Per-session coordinator that tracks handoff state and routing decisions.
 *
 * <p>Mirrors Python's {@code HandoffOrchestrator} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_orchestrator.py}.</p>
 */
public class HandoffOrchestrator {

    public static final String COORDINATOR_STATE_KEY = "__handoff_coordinator__";
    public static final String HANDOFF_HISTORY_KEY = "__handoff_history__";

    private static final Logger LOGGER = Logger.getLogger(HandoffOrchestrator.class.getName());

    private final int maxHandoffs;
    private final HandoffTerminationCondition terminationCondition;
    private final Map<String, Set<String>> routeGraph;
    private int handoffCount;
    private String currentAgentId;
    private CompletableFuture<Object> doneFuture;

    public HandoffOrchestrator(String startAgentId, List<String> registeredAgents) {
        this(startAgentId, registeredAgents, null);
    }

    public HandoffOrchestrator(String startAgentId, List<String> registeredAgents, HandoffConfig config) {
        List<HandoffRoute> routes = config == null ? List.of() : config.getRoutes();
        this.maxHandoffs = config == null ? 10 : config.getMaxHandoffs();
        this.terminationCondition = config == null ? null : config.getTerminationCondition();
        this.currentAgentId = startAgentId;
        this.routeGraph = buildRouteGraph(registeredAgents, routes == null ? List.of() : routes);
        LOGGER.fine(() -> "[HandoffOrchestrator] created start=%s max_handoffs=%d"
                .formatted(startAgentId, maxHandoffs));
    }

    public static Map<String, Set<String>> buildRouteGraph(List<String> agents, List<HandoffRoute> routes) {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (String agent : agents == null ? List.<String>of() : agents) {
            graph.put(agent, new LinkedHashSet<>());
        }
        if (routes != null && !routes.isEmpty()) {
            for (HandoffRoute route : routes) {
                graph.computeIfAbsent(route.getSource(), ignored -> new LinkedHashSet<>()).add(route.getTarget());
            }
            return graph;
        }
        for (String source : graph.keySet()) {
            for (String target : graph.keySet()) {
                if (!source.equals(target)) {
                    graph.get(source).add(target);
                }
            }
        }
        return graph;
    }

    public CompletableFuture<Boolean> requestHandoff(String targetId) {
        return requestHandoff(targetId, null);
    }

    public CompletableFuture<Boolean> requestHandoff(String targetId, String reason) {
        if (handoffCount >= maxHandoffs) {
            LOGGER.fine(() -> "[HandoffOrchestrator] max_handoffs reached, rejecting -> " + targetId);
            return CompletableFuture.completedFuture(false);
        }
        if (terminationCondition != null && terminationCondition.shouldTerminate(this)) {
            LOGGER.fine(() -> "[HandoffOrchestrator] termination_condition=True, rejecting -> " + targetId);
            return CompletableFuture.completedFuture(false);
        }
        Set<String> allowed = routeGraph.getOrDefault(currentAgentId, Set.of());
        if (!allowed.contains(targetId)) {
            LOGGER.warning("[HandoffOrchestrator] route %s -> %s not allowed".formatted(currentAgentId, targetId));
            return CompletableFuture.completedFuture(false);
        }
        handoffCount += 1;
        currentAgentId = targetId;
        LOGGER.fine(() -> "[HandoffOrchestrator] handoff approved -> %s count=%d"
                .formatted(targetId, handoffCount));
        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Void> complete(Object result) {
        if (!doneFuture().isDone()) {
            doneFuture().complete(result);
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> error(Throwable exception) {
        if (!doneFuture().isDone()) {
            doneFuture().completeExceptionally(exception);
        }
        return CompletableFuture.completedFuture(null);
    }

    public void saveToSession(SessionStatePort session) {
        session.updateState(Map.of(
                COORDINATOR_STATE_KEY,
                Map.of(
                        "current_agent_id", currentAgentId,
                        "handoff_count", handoffCount
                )
        ));
    }

    public void saveToSession(AgentTeamSession session) {
        saveToSession(new AgentTeamSessionPort(session));
    }

    public void saveToSession(BaseSession session) {
        saveToSession(new BaseSessionPort(session));
    }

    public static HandoffOrchestrator restoreFromSession(SessionStatePort session,
                                                         String startAgentId,
                                                         List<String> registeredAgents,
                                                         HandoffConfig config) {
        HandoffOrchestrator coordinator = new HandoffOrchestrator(startAgentId, registeredAgents, config);
        Object snapshot = session.getState(COORDINATOR_STATE_KEY);
        if (snapshot instanceof Map<?, ?> map && !map.isEmpty()) {
            Object current = map.get("current_agent_id");
            Object count = map.get("handoff_count");
            if (current != null) {
                coordinator.currentAgentId = String.valueOf(current);
            }
            if (count instanceof Number number) {
                coordinator.handoffCount = number.intValue();
            } else if (count != null) {
                coordinator.handoffCount = Integer.parseInt(String.valueOf(count));
            }
        }
        return coordinator;
    }

    public static HandoffOrchestrator restoreFromSession(AgentTeamSession session,
                                                         String startAgentId,
                                                         List<String> registeredAgents,
                                                         HandoffConfig config) {
        return restoreFromSession(new AgentTeamSessionPort(session), startAgentId, registeredAgents, config);
    }

    public static HandoffOrchestrator restoreFromSession(BaseSession session,
                                                         String startAgentId,
                                                         List<String> registeredAgents,
                                                         HandoffConfig config) {
        return restoreFromSession(new BaseSessionPort(session), startAgentId, registeredAgents, config);
    }

    public CompletableFuture<Object> doneFuture() {
        if (doneFuture == null) {
            doneFuture = new CompletableFuture<>();
        }
        return doneFuture;
    }

    public int getHandoffCount() {
        return handoffCount;
    }

    public String getCurrentAgentId() {
        return currentAgentId;
    }

    public int getMaxHandoffs() {
        return maxHandoffs;
    }

    public Map<String, Set<String>> getRouteGraph() {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : routeGraph.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return copy;
    }

    /**
     * Narrow session-state port matching Python session objects with `get_state` and `update_state`.
     */
    public interface SessionStatePort {
        Object getState(String key);

        void updateState(Map<String, Object> update);
    }

    private record AgentTeamSessionPort(AgentTeamSession session) implements SessionStatePort {
        @Override
        public Object getState(String key) {
            return session.getState(key);
        }

        @Override
        public void updateState(Map<String, Object> update) {
            session.updateState(update);
        }
    }

    private record BaseSessionPort(BaseSession session) implements SessionStatePort {
        @Override
        public Object getState(String key) {
            return session.getState(key);
        }

        @Override
        public void updateState(Map<String, Object> update) {
            session.updateState(update);
        }
    }
}
