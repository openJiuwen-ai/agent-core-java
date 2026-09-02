/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emit tool call/result chunks for CLI rendering.
 * 
 * @since 0.1.7
 */
public class ToolTrackingRail extends AgentRail {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * High priority so the {@code tool_call} chunk is emitted before any
     * interrupt rail (priority 90) can abort the callback chain, fixing
     * issue #131 (tool_call event missing before interruption).
     *
     * @return the priority
     * @since 0.1.7
     */
    @Override
    public int getPriority() {
        return 100;
    }

    /**
     * beforeToolCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (ctx == null || ctx.getSession() == null || !(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool_name", inputs.getToolName());
        payload.put("tool_args", normalizeArgs(inputs.getToolArgs()));
        ctx.getSession().writeStream(new OutputSchema("tool_call", 0, payload));
    }

    /**
     * afterToolCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        if (ctx == null || ctx.getSession() == null || !(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool_name", inputs.getToolName());
        payload.put("tool_args", normalizeArgs(inputs.getToolArgs()));
        payload.putAll(buildToolResultPayload(inputs.getToolName(), inputs.getToolResult()));
        ctx.getSession().writeStream(new OutputSchema("tool_result", 0, payload));
    }

    /**
     * buildToolResultPayload.
     * 
     * @param toolName toolName
     * @param toolResult toolResult
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Object> buildToolResultPayload(String toolName, Object toolResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool_result", toolResult != null ? String.valueOf(toolResult) : "");
        if (!"read_file".equals(toolName) || toolResult == null) {
            return payload;
        }
        if (!(extractData(toolResult) instanceof Map<?, ?> data)) {
            return payload;
        }
        Object content = data.get("content");
        if (content instanceof byte[] bytes) {
            payload.put("tool_result", new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        } else if (content != null) {
            payload.put("tool_result", String.valueOf(content));
        }
        Object lineCount = data.get("line_count");
        if (lineCount instanceof Number number) {
            payload.put("line_count", number.intValue());
        } else if (lineCount != null) {
            try {
                payload.put("line_count", Integer.parseInt(String.valueOf(lineCount)));
            } catch (NumberFormatException ignored) {
                // Python ignores invalid line_count values.
            }
        }
        return payload;
    }

    /**
     * normalizeArgs.
     * 
     * @param toolArgs toolArgs
     * @return the result
     * @since 0.1.7
     */
    private static Object normalizeArgs(Object toolArgs) {
        if (toolArgs instanceof String text) {
            try {
                return MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {
                });
            } catch (JsonProcessingException ignored) {
                return text;
            }
        }
        return toolArgs;
    }

    /**
     * extractData.
     * 
     * @param toolResult toolResult
     * @return the result
     * @since 0.1.7
     */
    private static Object extractData(Object toolResult) {
        try {
            return toolResult.getClass().getMethod("getData").invoke(toolResult);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
