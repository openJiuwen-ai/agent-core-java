/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool that signals control transfer to a target agent.
 *
 * <p>Mirrors Python's {@code HandoffTool} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_tool.py}.</p>
 */
public class HandoffTool extends Tool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String targetId;

    public HandoffTool(String targetId) {
        this(targetId, "");
    }

    public HandoffTool(String targetId, String targetDescription) {
        super(buildCard(targetId, targetDescription));
        this.targetId = targetId;
    }

    public String getTargetId() {
        return targetId;
    }

    /**
     * Python-compatible invoke entrypoint accepting map, JSON string, plain string, or any object.
     *
     * @param inputs raw tool inputs
     * @return handoff signal payload
     */
    public Map<String, Object> invokePayload(Object inputs) {
        return buildPayload(normalizeInputs(inputs));
    }

    /**
     * Python-compatible stream entrypoint yielding the single invoke result.
     *
     * @param inputs raw tool inputs
     * @return single-item iterator
     */
    public Iterator<Map<String, Object>> streamPayload(Object inputs) {
        return List.of(invokePayload(inputs)).iterator();
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return buildPayload(inputs);
    }

    @Override
    protected Iterator<Object> streamInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return List.<Object>of(buildPayload(inputs)).iterator();
    }

    private static ToolCard buildCard(String targetId, String targetDescription) {
        String toolName = "transfer_to_" + targetId;
        String description = "Transfer the current task to " + targetId + " for processing.";
        if (targetDescription != null && !targetDescription.isBlank()) {
            description += " " + targetDescription;
        }
        return new ToolCard(toolName, toolName, description, inputParams());
    }

    private static Map<String, Object> inputParams() {
        Map<String, Object> reason = new LinkedHashMap<>();
        reason.put("type", "string");
        reason.put("description", "Reason for handoff: briefly explain why the task is being transferred.");

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "string");
        message.put("description", "Context information passed to the next agent (optional).");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("reason", reason);
        properties.put("message", message);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", properties);
        params.put("required", List.of("reason"));
        return params;
    }

    private static Map<String, Object> normalizeInputs(Object inputs) {
        if (inputs instanceof String text) {
            return parseStringInput(text);
        }
        if (inputs instanceof Map<?, ?> rawMap) {
            return stringObjectMap(rawMap);
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> parseStringInput(String text) {
        try {
            Object parsed = OBJECT_MAPPER.readValue(text, Object.class);
            if (parsed instanceof Map<?, ?> rawMap) {
                return stringObjectMap(rawMap);
            }
            return new LinkedHashMap<>();
        } catch (JsonProcessingException ignored) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("reason", text);
            return fallback;
        }
    }

    private Map<String, Object> buildPayload(Map<String, Object> inputs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(HandoffSignal.HANDOFF_TARGET_KEY, targetId);
        payload.put(HandoffSignal.HANDOFF_MESSAGE_KEY, stringValue(inputs.get("message")));
        payload.put(HandoffSignal.HANDOFF_REASON_KEY, stringValue(inputs.get("reason")));
        return payload;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
