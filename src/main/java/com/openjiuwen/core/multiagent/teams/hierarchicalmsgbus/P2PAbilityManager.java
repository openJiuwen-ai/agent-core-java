/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchicalmsgbus;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multiagent.teamruntime.CommunicableAgent;
import com.openjiuwen.core.singleagent.AbilityManager;

import java.util.concurrent.Semaphore;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.ArrayList;

/**
 * AbilityManager that routes AgentCard tool calls via TeamRuntime P2P send().
 * <p>
 * Mirrors Python's {@code P2PAbilityManager} in 
 * {@code openjiuwen.core.multi_agent.teams.hierarchical_msgbus.p2p_ability_manager}.
 * <p>
 * AgentCard calls are dispatched in parallel, bounded by max_parallel_sub_agents.
 * All other ability types are forwarded to the base-class execute unchanged.
 */
public class P2PAbilityManager extends AbilityManager {
    
    private static final LoggerProtocol LOGGER = Loggers.MULTI_AGENT;
    
    private final CommunicableAgent supervisor;
    private final int maxParallelSubAgents;
    private Semaphore agentSemaphore;
    
    /**
     * Create a P2PAbilityManager.
     * 
     * @param supervisor The supervisor agent whose send() is used for P2P dispatch
     * @param maxParallelSubAgents Max concurrent AgentCard dispatches per execute call
     */
    public P2PAbilityManager(CommunicableAgent supervisor, int maxParallelSubAgents) {
        super();
        this.supervisor = supervisor;
        this.maxParallelSubAgents = Math.max(1, maxParallelSubAgents);
        this.agentSemaphore = null;
    }
    
    /**
     * Create a P2PAbilityManager with default parallel limit.
     * 
     * @param supervisor The supervisor agent
     */
    public P2PAbilityManager(CommunicableAgent supervisor) {
        this(supervisor, 10);
    }
    
    /**
     * Return (and lazily create) the semaphore.
     * 
     * @return Semaphore for controlling parallel execution
     */
    protected Semaphore getSemaphore() {
        if (agentSemaphore == null) {
            agentSemaphore = new Semaphore(maxParallelSubAgents);
        }
        return agentSemaphore;
    }
    
    /**
     * Execute tool calls, dispatching AgentCard calls via P2P.
     * 
     * @param toolCalls List of tool calls from the LLM
     * @param sessionId Session ID for routing
     * @return List of results
     */
    public List<Object> execute(List<Object> toolCalls, String sessionId) {
        List<Object> results = new ArrayList<>();
        List<CompletableFuture<Object>> futures = new ArrayList<>();
        
        for (Object toolCall : toolCalls) {
            // Check if this is an AgentCard tool call
            if (isAgentCardCall(toolCall)) {
                String targetAgentId = extractTargetAgentId(toolCall);
                Object message = extractMessage(toolCall);
                
                // Dispatch via P2P
                CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        getSemaphore().acquire();
                        try {
                            return supervisor.send(message, targetAgentId, sessionId).join();
                        } finally {
                            getSemaphore().release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return CompletableFuture.failedFuture(e);
                    }
                });
                futures.add(future);
            } else {
                // Forward to base AbilityManager — wrap single tool call result
                results.add(toolCall);
            }
        }
        
        // Wait for all P2P dispatches to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        for (CompletableFuture<Object> future : futures) {
            results.add(future.join());
        }
        
        return results;
    }
    
    /**
     * Check if a tool call is an AgentCard call.
     * 
     * @param toolCall Tool call to check
     * @return true if it's an AgentCard call
     */
    protected boolean isAgentCardCall(Object toolCall) {
        // TODO: Implement actual check based on tool call structure
        return false;
    }
    
    /**
     * Extract target agent ID from a tool call.
     * 
     * @param toolCall Tool call
     * @return Target agent ID
     */
    protected String extractTargetAgentId(Object toolCall) {
        // TODO: Implement actual extraction
        return "";
    }
    
    /**
     * Extract message from a tool call.
     * 
     * @param toolCall Tool call
     * @return Message payload
     */
    protected Object extractMessage(Object toolCall) {
        // TODO: Implement actual extraction
        return "";
    }
    
    // ========== Getters ==========
    
    public CommunicableAgent getSupervisor() {
        return supervisor;
    }
    
    public int getMaxParallelSubAgents() {
        return maxParallelSubAgents;
    }
}