// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.schema.Param;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Single Agent Base Class.
 * 
 * <p><strong>Design principles:</strong>
 * <ul>
 *   <li>Card is required (defines what the Agent is)</li>
 *   <li>Config is optional (defines how the Agent runs)</li>
 *   <li>All configuration methods support chaining</li>
 * </ul>
 * 
 * <p>Python reference: {@code agent-core/openjiuwen/core/single_agent/agent.py}
 */
public abstract class BaseAgent {
    
    private static final LoggerProtocol logger = Loggers.AGENT;
    
    /**
     * Agent card (required).
     */
    protected final AgentCard card;
    
    /**
     * Ability manager.
     */
    protected final AbilityManager abilityManager;
    
    /**
     * Initializes the agent.
     *
     * @param card the agent card (required)
     */
    protected BaseAgent(AgentCard card) {
        this.card = card;
        this.abilityManager = new AbilityManager();
    }
    
    // ========== Configuration Interface ==========
    
    /**
     * Sets the configuration.
     *
     * @param config the configuration object
     * @return this agent for chaining
     */
    public abstract BaseAgent configure(Object config);
    
    // ========== Ability Management Interface ==========
    
    /**
     * Gets the ability manager.
     *
     * @return the ability manager
     */
    public AbilityManager getAbilityManager() {
        return abilityManager;
    }
    
    /**
     * Gets the agent card.
     *
     * @return the agent card
     */
    public AgentCard getCard() {
        return card;
    }
    
    /**
     * Adds an ability.
     *
     * @param ability the ability Card (ToolCard, WorkflowCard, AgentCard, or McpServerConfig)
     * @return this agent for chaining
     */
    public BaseAgent addAbility(Object ability) {
        abilityManager.add(ability);
        return this;
    }
    
    /**
     * Adds multiple abilities.
     *
     * @param abilities the list of ability Cards
     * @return this agent for chaining
     */
    public BaseAgent addAbility(List<?> abilities) {
        for (Object ability : abilities) {
            abilityManager.add(ability);
        }
        return this;
    }
    
    /**
     * Removes an ability by name.
     *
     * @param name the ability name to remove
     * @return this agent for chaining
     */
    public BaseAgent removeAbility(String name) {
        abilityManager.remove(name);
        return this;
    }
    
    /**
     * Removes multiple abilities by name.
     *
     * @param names the list of ability names to remove
     * @return this agent for chaining
     */
    public BaseAgent removeAbility(List<String> names) {
        for (String name : names) {
            abilityManager.remove(name);
        }
        return this;
    }
    
    /**
     * Gets an ability Card by name.
     *
     * @param name the ability name
     * @return the ability Card, or empty if not found
     */
    public Optional<Object> getAbility(String name) {
        return abilityManager.get(name);
    }
    
    /**
     * Lists all ability Cards.
     *
     * @return list of all ability Cards
     */
    public List<Object> listAbilities() {
        return abilityManager.list();
    }
    
    /**
     * Gets ToolInfo list for LLM usage.
     *
     * @param names optional filter by ability names (null means all)
     * @return future containing list of ToolInfo objects
     */
    public CompletableFuture<List<ToolInfo>> listToolInfo(List<String> names) {
        return abilityManager.listToolInfo(names);
    }
    
    // ========== Query Interface ==========
    
    /**
     * Converts current Agent to ToolInfo (for use as sub-agent).
     *
     * @return ToolInfo representing this agent
     */
    public ToolInfo getToolInfo() {
        // Build parameters from agent card's input_params
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        
        List<Param> inputParams = card.getAgentInputParams();
        if (inputParams != null) {
            for (Param param : inputParams) {
                Map<String, Object> propSchema = new LinkedHashMap<>();
                propSchema.put("type", param.getType() != null ? param.getType().getValue() : "string");
                propSchema.put("description", param.getDescription() != null ? param.getDescription() : "");
                properties.put(param.getName(), propSchema);
                
                if (param.isRequired()) {
                    required.add(param.getName());
                }
            }
        }
        
        params.put("properties", properties);
        params.put("required", required);
        
        return new ToolInfo(
            card.getName(),
            card.getDescription() != null ? card.getDescription() : "",
            params
        );
    }
    
    // ========== Execution Interface ==========
    
    /**
     * Executes a single ability call.
     *
     * @param toolCall the tool call from LLM
     * @param session the session instance
     * @return future containing list with one execution result
     */
    public CompletableFuture<List<AbilityManager.ExecutionResult>> executeAbility(
            ToolCall toolCall, Session session) {
        return executeAbility(List.of(toolCall), session);
    }
    
    /**
     * Executes ability calls (supports parallel execution).
     *
     * @param toolCalls list of tool calls from LLM
     * @param session the session instance
     * @return future containing list of execution results
     */
    public CompletableFuture<List<AbilityManager.ExecutionResult>> executeAbility(
            List<ToolCall> toolCalls, Session session) {
        
        // Execute all tool calls in parallel
        List<CompletableFuture<AbilityManager.ExecutionResult>> futures = new ArrayList<>();
        for (ToolCall toolCall : toolCalls) {
            CompletableFuture<AbilityManager.ExecutionResult> future = 
                abilityManager.execute(toolCall, session)
                    .exceptionally(ex -> {
                        String errorMsg = "Ability execution error: " + ex.getMessage();
                        logger.error(errorMsg);
                        ToolMessage toolMessage = new ToolMessage(toolCall.getId(), errorMsg);
                        return new AbilityManager.ExecutionResult(null, toolMessage);
                    });
            futures.add(future);
        }
        
        // Wait for all to complete
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
    }
    
    /**
     * Batch execution (can pass config at runtime to override).
     *
     * @param inputs agent input, supports:
     *               - Map: must contain "user_input" and "session_id"
     *               - String: used directly as user_input
     * @param session session object (optional)
     * @return future containing agent output result
     */
    public abstract CompletableFuture<Object> invoke(Object inputs, Session session);
    
    /**
     * Stream execution (can pass config at runtime to override).
     *
     * @param inputs agent input, supports:
     *               - Map: must contain "user_input" and "session_id"
     *               - String: used directly as user_input
     * @param session session object (optional)
     * @param streamModes stream output modes (optional)
     * @return future containing an iterator over output chunks (async iterator equivalent)
     */
    public abstract CompletableFuture<Iterator<Object>> stream(
        Object inputs, Session session, List<StreamMode> streamModes);
}
