/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.rails;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Emit tool call and tool result chunks for CLI rendering.
 *
 * <p>Mirrors Python's {@code ToolTrackingRail} in
 * {@code openjiuwen.harness.cli.rails.tool_tracker}.</p>
 */
public class ToolTrackingRail extends AgentRail {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public ToolTrackingRail() {
        setPriority(5);
    }

    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (ctx == null || ctx.getSession() == null || !(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool_name", safe(inputs.getToolName()));
        payload.put("tool_args", normalizeToolArgs(inputs.getToolArgs()));
        ctx.getSession().writeStream(new OutputSchema("tool_call", 0, payload));
    }

    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        if (ctx == null || ctx.getSession() == null || !(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool_name", safe(inputs.getToolName()));
        payload.put("tool_args", normalizeToolArgs(inputs.getToolArgs()));
        payload.putAll(buildToolResultPayload(safe(inputs.getToolName()), inputs.getToolResult()));
        ctx.getSession().writeStream(new OutputSchema("tool_result", 0, payload));
    }

    private static Object normalizeToolArgs(Object toolArgs) {
        if (!(toolArgs instanceof String text)) {
            return toolArgs;
        }
        try {
            return OBJECT_MAPPER.readValue(text, Object.class);
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            return toolArgs;
        }
    }

    private static Map<String, Object> buildToolResultPayload(String toolName, Object toolResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool_result", toolResult != null ? String.valueOf(toolResult) : "");
        if (!"read_file".equals(toolName) || toolResult == null) {
            return payload;
        }

        Optional<Object> data = readProperty(toolResult, "data");
        if (data.isEmpty() || !(data.get() instanceof Map<?, ?> map)) {
            return payload;
        }
        Object content = map.get("content");
        if (content instanceof byte[] bytes) {
            payload.put("tool_result", new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        } else if (content != null) {
            payload.put("tool_result", String.valueOf(content));
        }
        Object lineCount = map.get("line_count");
        if (lineCount instanceof Number number) {
            payload.put("line_count", number.intValue());
        } else if (lineCount != null) {
            try {
                payload.put("line_count", Integer.parseInt(String.valueOf(lineCount)));
            } catch (NumberFormatException ignored) {
                // Keep Python behavior: omit invalid line_count.
            }
        }
        return payload;
    }

    private static Optional<Object> readProperty(Object target, String propertyName) {
        if (target == null || propertyName == null || propertyName.isBlank()) {
            return Optional.empty();
        }
        String suffix = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        for (String methodName : java.util.List.of("get" + suffix, propertyName)) {
            try {
                Method method = target.getClass().getMethod(methodName);
                method.setAccessible(true);
                return Optional.ofNullable(method.invoke(target));
            } catch (ReflectiveOperationException ignored) {
                // Try next accessor shape.
            }
        }
        return Optional.empty();
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
