/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import java.util.*;
import java.util.concurrent.CompletableFuture;

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
    private final Map<String, Set<String>> routeGraph;
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
        
        handoffCount++;
        currentAgentId = targetId;
        return true;
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
}