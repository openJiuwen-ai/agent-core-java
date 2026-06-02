/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Tool that signals control transfer to a target agent.
 * <p>
 * Mirrors Python's {@code HandoffTool} in 
 * {@code openjiuwen.core.multi_agent.teams.handoff.handoff_tool}.
 * <p>
 * Injected automatically by HandoffTeam into every agent's AbilityManager.
 * The tool name exposed to the LLM is {@code transfer_to_{target_id}}.
 */
public class HandoffTool extends Tool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final String targetId;
    
    /**
     * Create a HandoffTool for the specified target agent.
     * 
     * @param targetId ID of the agent to hand off to
     * @param targetDescription Optional description of the target agent
     */
    public HandoffTool(String targetId, String targetDescription) {
        super(createToolCard(targetId, targetDescription));
        this.targetId = targetId;
    }
    
    /**
     * Create a HandoffTool with default description.
     * 
     * @param targetId ID of the agent to hand off to
     */
    public HandoffTool(String targetId) {
        this(targetId, "");
    }
    
    private static ToolCard createToolCard(String targetId, String targetDescription) {
        String toolName = "transfer_to_" + targetId;
        String description = "Transfer the current task to " + targetId + " for processing.";
        if (targetDescription != null && !targetDescription.isEmpty()) {
            description += " " + targetDescription;
        }
        
        Map<String, Object> inputParams = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        // reason property
        Map<String, Object> reasonProp = new HashMap<>();
        reasonProp.put("type", "string");
        reasonProp.put("description", "Reason for handoff: briefly explain why the task is being transferred.");
        properties.put("reason", reasonProp);
        
        // message property
        Map<String, Object> messageProp = new HashMap<>();
        messageProp.put("type", "string");
        messageProp.put("description", "Context information passed to the next agent (optional).");
        properties.put("message", messageProp);
        
        inputParams.put("type", "object");
        inputParams.put("properties", properties);
        inputParams.put("required", java.util.List.of("reason"));
        
        return ToolCard.builder()
                .id(toolName)
                .name(toolName)
                .description(description)
                .inputParams(inputParams)
                .build();
    }
    
    /**
     * Execute the handoff tool.
     * <p>
     * Returns a dict with handoff signal keys consumed by HandoffSignal extraction.
     * 
     * @param inputs Tool arguments from the LLM
     * @param kwargs Additional execution parameters
     * @return Map with handoff signal keys
     */
    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Map<String, Object> result = new HashMap<>();
        result.put(HandoffSignal.HANDOFF_TARGET_KEY, targetId);
        
        // Extract reason and message from inputs
        Object reason = inputs != null ? inputs.get("reason") : null;
        Object message = inputs != null ? inputs.get("message") : null;

        result.put(HandoffSignal.HANDOFF_REASON_KEY, reason != null ? reason.toString() : "");
        result.put(HandoffSignal.HANDOFF_MESSAGE_KEY, message != null ? message.toString() : "");
        
        return result;
    }

    /**
     * Execute the handoff tool with Python-compatible dynamic input normalization.
     *
     * @param inputs map, JSON string, plain string, or null
     * @return Map with handoff signal keys
     * @throws Exception when execution fails
     */
    public Object invoke(Object inputs) throws Exception {
        return invoke(normalizeInputs(inputs), Map.of());
    }
    
    /**
     * Get the target agent ID for this handoff tool.
     * 
     * @return Target agent ID
     */
    public String getTargetId() {
        return targetId;
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        Object result = invoke(inputs, kwargs);
        return Collections.singletonList(result).iterator();
    }

    /**
     * Stream execution with Python-compatible dynamic input normalization.
     *
     * @param inputs map, JSON string, plain string, or null
     * @return single-result iterator
     * @throws Exception when execution fails
     */
    public Iterator<Object> stream(Object inputs) throws Exception {
        return stream(normalizeInputs(inputs), Map.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeInputs(Object inputs) {
        if (inputs instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        if (inputs instanceof String text) {
            Map<String, Object> parsed = parseJsonMap(text);
            if (parsed != null) {
                return parsed;
            }
            return new HashMap<>(Map.of("reason", text));
        }
        return new HashMap<>();
    }

    private Map<String, Object> parseJsonMap(String text) {
        try {
            return OBJECT_MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return null;
        }
    }
}
