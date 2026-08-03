/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.session.AgentGroupSessionApi;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Public class HandoffOrchestrator used by the Java parity implementation.
 *
 * @since 1.0
 */
public class HandoffOrchestrator {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String COORDINATOR_STATE_KEY = "__handoff_coordinator__";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String HANDOFF_HISTORY_KEY = "__handoff_history__";

    private final int maxHandoffs;
    private final com.openjiuwen.core.multi_agent.teams.handoff.HandoffTerminationCondition terminationCondition;
    private final Map<String, Set<String>> routeGraph;
    private int handoffCount = 0;
    private String currentAgentId;
    private CompletableFuture<Object> doneFuture;

    /**
     * Auto-generated for codecheck compliance.
     */
    public HandoffOrchestrator(String startAgentId, List<String> registeredAgents, HandoffConfig config) {
        this.currentAgentId = startAgentId;
        this.maxHandoffs = config != null ? config.getMaxHandoffs() : 10;
        this.terminationCondition = config != null ? config.getTerminationCondition() : null;
        List<HandoffRoute> routes = List.of();
        if (config != null && config.getRoutes() != null) {
            routes = config.getRoutes().stream()
                .map(r -> new HandoffRoute(r.getSource(), r.getTarget()))
                .toList();
        }
        this.routeGraph = buildRouteGraph(registeredAgents, routes);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public HandoffOrchestrator(String startAgentId, List<String> registeredAgents) {
        this(startAgentId, registeredAgents, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Map<String, Set<String>> buildRouteGraph(List<String> agents, List<HandoffRoute> routes) {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (String agent : agents) {
            graph.put(agent, new LinkedHashSet<>());
        }
        if (routes != null && !routes.isEmpty()) {
            for (HandoffRoute route : routes) {
                graph.computeIfAbsent(route.getSource(), ignored -> new LinkedHashSet<>()).add(route.getTarget());
            }
            return graph;
        }
        for (String src : agents) {
            for (String dst : agents) {
                if (!src.equals(dst)) {
                    graph.get(src).add(dst);
                }
            }
        }
        return graph;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean requestHandoff(String targetId) {
        if (handoffCount >= maxHandoffs) {
            return false;
        }
        if (terminationCondition != null && terminationCondition.shouldTerminate(this)) {
            return false;
        }
        if (!routeGraph.getOrDefault(currentAgentId, Set.of()).contains(targetId)) {
            return false;
        }
        handoffCount += 1;
        currentAgentId = targetId;
        return true;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void complete(Object result) {
        if (!doneFuture().isDone()) {
            doneFuture().complete(result);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void error(Throwable throwable) {
        if (!doneFuture().isDone()) {
            doneFuture().completeExceptionally(throwable);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void saveToSession(AgentGroupSessionApi session) {
        if (session == null) {
            return;
        }
        session.updateState(Map.of(
                COORDINATOR_STATE_KEY, Map.of(
                        "current_agent_id", currentAgentId,
                        "handoff_count", handoffCount
                )
        ));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static HandoffOrchestrator restoreFromSession(AgentGroupSessionApi session,
                                                          String startAgentId,
                                                          List<String> registeredAgents,
                                                          HandoffConfig config) {
        HandoffOrchestrator orchestrator = new HandoffOrchestrator(startAgentId, registeredAgents, config);
        Object state = session != null ? session.getState(COORDINATOR_STATE_KEY) : null;
        if (state instanceof Map<?, ?> map) {
            Object current = map.get("current_agent_id");
            Object count = map.get("handoff_count");
            if (current instanceof String currentId) {
                orchestrator.currentAgentId = currentId;
            }
            if (count instanceof Number n) {
                orchestrator.handoffCount = n.intValue();
            }
        }
        return orchestrator;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletableFuture<Object> doneFuture() {
        if (doneFuture == null) {
            doneFuture = new CompletableFuture<>();
        }
        return doneFuture;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getHandoffCount() {
        return handoffCount;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getCurrentAgentId() {
        return currentAgentId;
    }
}
