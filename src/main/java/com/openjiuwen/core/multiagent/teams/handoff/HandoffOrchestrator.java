/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Per-session coordinator for HandoffTeam.
 * <p>
 * Mirrors Python's {@code HandoffOrchestrator} in 
 * {@code openjiuwen.core.multi_agent.teams.handoff.handoff_orchestrator}.
 * <p>
 * Tracks handoff state and routing decisions.
 */
public class HandoffOrchestrator {
    
    public static final String COORDINATOR_STATE_KEY = "__handoff_coordinator__";
    public static final String HANDOFF_HISTORY_KEY = "__handoff_history__";
    
    private final int maxHandoffs;
    private final Function<Object, Object> terminationCondition;
    private final Map<String, Set<String>> routeGraph;
    private CompletableFuture<Object> doneFuture;
    private int handoffCount;
    private String currentAgentId;
    
    /**
     * Build route graph from agent list and routes.
     * 
     * @param agents List of all agent IDs
     * @param routes Explicit routing rules (empty = full-mesh)
     * @return Adjacency graph
     */
    public static Map<String, Set<String>> buildRouteGraph(List<String> agents, List<HandoffRoute> routes) {
        Map<String, Set<String>> graph = new HashMap<>();
        for (String a : agents) {
            graph.put(a, new HashSet<>());
        }
        
        if (routes.isEmpty()) {
            // Full mesh: each agent can hand off to any other
            for (String src : agents) {
                for (String tgt : agents) {
                    if (!src.equals(tgt)) {
                        graph.get(src).add(tgt);
                    }
                }
            }
        } else {
            // Explicit routes only
            for (HandoffRoute r : routes) {
                graph.computeIfAbsent(r.getSource(), k -> new HashSet<>()).add(r.getTarget());
            }
        }
        
        return graph;
    }
    
    public HandoffOrchestrator(String startAgentId, List<String> registeredAgents, HandoffConfig config) {
        this.maxHandoffs = config != null ? config.getMaxHandoffs() : 10;
        this.terminationCondition = config != null ? config.getTerminationCondition() : null;
        this.routeGraph = buildRouteGraph(registeredAgents, 
            config != null ? config.getRoutes() : Collections.emptyList());
        this.handoffCount = 0;
        this.currentAgentId = startAgentId;
    }
    
    /**
     * Attempt to approve a handoff to target.
     * 
     * @param targetId Target agent ID
     * @return true if handoff is approved
     */
    public boolean requestHandoff(String targetId) {
        Set<String> allowedTargets = routeGraph.getOrDefault(currentAgentId, Set.of());
        
        if (!allowedTargets.contains(targetId)) {
            return false; // Route not allowed
        }
        
        if (handoffCount >= maxHandoffs) {
            return false; // Max handoffs reached
        }

        if (terminationCondition != null) {
            Object result = terminationCondition.apply(this);
            if (result instanceof CompletionStage<?> stage) {
                result = stage.toCompletableFuture().join();
            }
            if (Boolean.TRUE.equals(result)) {
                return false;
            }
        }
        
        handoffCount++;
        currentAgentId = targetId;
        return true;
    }

    /**
     * Complete the handoff chain with a result.
     *
     * @param result final result
     */
    public void complete(Object result) {
        getDoneFuture().complete(result);
    }

    /**
     * Complete the handoff chain exceptionally.
     *
     * @param exception exception to propagate
     */
    public void error(Throwable exception) {
        getDoneFuture().completeExceptionally(exception);
    }

    /**
     * Persist coordinator state to a session.
     *
     * @param session session to update
     */
    public void saveToSession(com.openjiuwen.core.session.Session session) {
        session.updateState(Map.of(COORDINATOR_STATE_KEY, Map.of(
                "current_agent_id", currentAgentId,
                "handoff_count", handoffCount
        )));
    }

    /**
     * Restore an orchestrator from session state.
     *
     * @param session session to read from
     * @param startAgentId start agent
     * @param registeredAgents registered agent IDs
     * @param config configuration
     * @return restored or fresh orchestrator
     */
    public static HandoffOrchestrator restoreFromSession(
            com.openjiuwen.core.session.Session session,
            String startAgentId,
            List<String> registeredAgents,
            HandoffConfig config) {
        HandoffOrchestrator orchestrator = new HandoffOrchestrator(startAgentId, registeredAgents, config);
        Object snapshot = session.getState(COORDINATOR_STATE_KEY);
        if (snapshot instanceof Map<?, ?> map) {
            Object current = map.get("current_agent_id");
            Object count = map.get("handoff_count");
            if (current instanceof String agentId) {
                orchestrator.currentAgentId = agentId;
            }
            if (count instanceof Number number) {
                orchestrator.handoffCount = number.intValue();
            }
        }
        return orchestrator;
    }

    public static HandoffOrchestrator restoreFromSession(
            com.openjiuwen.core.session.Session session,
            String startAgentId,
            List<String> registeredAgents) {
        return restoreFromSession(session, startAgentId, registeredAgents, null);
    }
    
    /**
     * Check if should terminate.
     * 
     * @return true if should terminate
     */
    public boolean shouldTerminate() {
        return handoffCount >= maxHandoffs;
    }
    
    // Getters
    public int getHandoffCount() { return handoffCount; }
    public String getCurrentAgentId() { return currentAgentId; }
    public int getMaxHandoffs() { return maxHandoffs; }
    public CompletableFuture<Object> getDoneFuture() {
        if (doneFuture == null) {
            doneFuture = new CompletableFuture<>();
        }
        return doneFuture;
    }
}
