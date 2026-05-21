/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;

import java.util.HashMap;
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
        Object reason = inputs.get("reason");
        Object message = inputs.get("message");
        
        if (reason != null) {
            result.put(HandoffSignal.HANDOFF_REASON_KEY, reason.toString());
        }
        if (message != null) {
            result.put(HandoffSignal.HANDOFF_MESSAGE_KEY, message.toString());
        }
        
        return result;
    }
    
    /**
     * Get the target agent ID for this handoff tool.
     * 
     * @return Target agent ID
     */
    public String getTargetId() {
        return targetId;
    }
}