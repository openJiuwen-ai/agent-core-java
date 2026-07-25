/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Emit tool call/result chunks for CLI rendering.
 */
public class ToolTrackingRail extends AgentRail {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    public int getPriority() {
        return 5;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    public CompletionStage<Void> beforeToolCall(AgentCallbackContext ctx) {
        if (ctx == null || ctx.getSession() == null || !(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return completed();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool_name", inputs.getToolName());
        payload.put("tool_args", normalizeArgs(inputs.getToolArgs()));
        ctx.getSession().writeStream(new OutputSchema("tool_call", 0, payload));
        return completed();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    public CompletionStage<Void> afterToolCall(AgentCallbackContext ctx) {
        if (ctx == null || ctx.getSession() == null || !(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return completed();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool_name", inputs.getToolName());
        payload.put("tool_args", normalizeArgs(inputs.getToolArgs()));
        payload.putAll(buildToolResultPayload(inputs.getToolName(), inputs.getToolResult()));
        ctx.getSession().writeStream(new OutputSchema("tool_result", 0, payload));
        return completed();
    }

    /**
     * Auto-generated for codecheck compliance.
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

    private static Object normalizeArgs(Object toolArgs) {
        if (toolArgs instanceof String text) {
            try {
                return MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
                return text;
            }
        }
        return toolArgs;
    }

    private static Object extractData(Object toolResult) {
        try {
            return toolResult.getClass().getMethod("getData").invoke(toolResult);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
