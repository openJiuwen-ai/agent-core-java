/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool that signals control transfer to a target agent.
 * <p>
 * Mirrors Python's {@code HandoffTool}. Injected automatically by
 * {@link HandoffTeam} into every agent's {@code AbilityManager}. The tool name
 * exposed to the LLM is {@code transfer_to_{target_id}}; invoking it returns a
 * payload carrying {@link HandoffSignal#HANDOFF_TARGET_KEY} which
 * {@link HandoffSignal#extract} consumes to drive the handoff chain.
 * </p>
 * 
 * @since 0.1.7
 */
public class HandoffTool extends Tool {
    private final String targetId;

    /**
     * Create a handoff tool targeting {@code targetId}.
     * 
     * @param targetId ID of the agent to hand off to.
     * @param targetDescription Optional description of the target agent appended
     *            to the tool description shown to the LLM.
     * @since 0.1.7
     */
    public HandoffTool(String targetId, String targetDescription) {
        super(buildCard(targetId, targetDescription));
        this.targetId = targetId;
    }

    /**
     * HandoffTool.
     * 
     * @param targetId targetId
     * @since 0.1.7
     */
    public HandoffTool(String targetId) {
        this(targetId, "");
    }

    /**
     * getTargetId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTargetId() {
        return targetId;
    }

    /**
     * Return a handoff signal payload dict consumed by
     * {@link HandoffSignal#extract}.
     * <p>
     * Accepts a dict of tool arguments ({@code reason} / {@code message}).
     * Missing values default to empty strings to match Python parity.
     * </p>
     * 
     * @param inputs inputs
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String message = inputs != null && inputs.get("message") != null ? String.valueOf(inputs.get("message")) : "";
        String reason = inputs != null && inputs.get("reason") != null ? String.valueOf(inputs.get("reason")) : "";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(HandoffSignal.HANDOFF_TARGET_KEY, targetId);
        payload.put(HandoffSignal.HANDOFF_MESSAGE_KEY, message);
        payload.put(HandoffSignal.HANDOFF_REASON_KEY, reason);
        return payload;
    }

    /**
     * Streaming variant — yields the single {@link #invoke} result.
     * 
     * @param inputs inputs
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Object result = invoke(inputs, kwargs);
        return List.of(result).iterator();
    }

    /**
     * buildCard.
     * 
     * @param targetId targetId
     * @param targetDescription targetDescription
     * @return the result
     * @since 0.1.7
     */
    private static ToolCard buildCard(String targetId, String targetDescription) {
        String toolName = "transfer_to_" + targetId;
        String description = "Transfer the current task to " + targetId + " for processing.";
        if (targetDescription != null && !targetDescription.isBlank()) {
            description += " " + targetDescription;
        }
        Map<String, Object> reasonProp = new LinkedHashMap<>();
        reasonProp.put("type", "string");
        reasonProp.put("description", "Reason for handoff: briefly explain why the task is being transferred.");
        Map<String, Object> messageProp = new LinkedHashMap<>();
        messageProp.put("type", "string");
        messageProp.put("description", "Context information passed to the next agent (optional).");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("reason", reasonProp);
        properties.put("message", messageProp);
        Map<String, Object> inputParams = new LinkedHashMap<>();
        inputParams.put("type", "object");
        inputParams.put("properties", properties);
        inputParams.put("required", List.of("reason"));
        return ToolCard.builder().id(toolName).name(toolName).description(description).inputParams(inputParams).build();
    }
}
