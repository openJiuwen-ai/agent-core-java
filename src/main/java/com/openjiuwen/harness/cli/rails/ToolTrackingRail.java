/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.rails;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Emits tool call and result chunks for CLI rendering.
 *
 * <p>Mirrors Python's {@code ToolTrackingRail} in
 * {@code openjiuwen/harness/cli/rails/tool_tracker.py}.</p>
 */
public class ToolTrackingRail extends AgentRail {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Object> JSON_VALUE_TYPE = new TypeReference<>() {
    };

    public ToolTrackingRail() {
        setPriority(5);
    }

    public static Map<String, Object> buildToolResultPayload(String toolName, Object toolResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool_result", toolResult == null ? "" : String.valueOf(toolResult));
        if (!"read_file".equals(toolName) || toolResult == null) {
            return payload;
        }

        Object dataValue = readProperty(toolResult, "data");
        if (!(dataValue instanceof Map<?, ?> data)) {
            return payload;
        }

        Object content = data.get("content");
        if (content != null) {
            if (content instanceof byte[] bytes) {
                payload.put("tool_result", new String(bytes, StandardCharsets.UTF_8));
            } else {
                payload.put("tool_result", String.valueOf(content));
            }
        }

        Object lineCount = data.get("line_count");
        Integer parsedLineCount = parseInteger(lineCount);
        if (parsedLineCount != null) {
            payload.put("line_count", parsedLineCount);
        }
        return payload;
    }

    @Override
    public CompletionStage<Void> beforeToolCall(AgentCallbackContext context) {
        AgentSessionApi session = context == null ? null : context.getSession();
        if (session == null) {
            return completed();
        }

        Object inputs = context.getInputs();
        String toolName = stringValue(readInput(inputs, "tool_name", "toolName"), "");
        Object toolArgs = normalizeToolArgs(defaultString(readInput(inputs, "tool_args", "toolArgs")));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool_name", toolName);
        payload.put("tool_args", toolArgs);
        session.writeStream(new OutputSchema("tool_call", 0, payload));
        return completed();
    }

    @Override
    public CompletionStage<Void> afterToolCall(AgentCallbackContext context) {
        AgentSessionApi session = context == null ? null : context.getSession();
        if (session == null) {
            return completed();
        }

        Object inputs = context.getInputs();
        String toolName = stringValue(readInput(inputs, "tool_name", "toolName"), "");
        Object toolArgs = normalizeToolArgs(defaultString(readInput(inputs, "tool_args", "toolArgs")));
        Object toolResult = readInput(inputs, "tool_result", "toolResult");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool_name", toolName);
        payload.put("tool_args", toolArgs);
        payload.putAll(buildToolResultPayload(toolName, toolResult));
        session.writeStream(new OutputSchema("tool_result", 0, payload));
        return completed();
    }

    private static Object normalizeToolArgs(Object toolArgs) {
        if (!(toolArgs instanceof String text)) {
            return toolArgs;
        }
        try {
            return OBJECT_MAPPER.readValue(text, JSON_VALUE_TYPE);
        } catch (JsonProcessingException exception) {
            return toolArgs;
        }
    }

    private static Object readInput(Object inputs, String snakeName, String camelName) {
        if (inputs instanceof ToolCallInputs toolCallInputs) {
            return switch (snakeName) {
                case "tool_name" -> toolCallInputs.getToolName();
                case "tool_args" -> toolCallInputs.getToolArgs();
                case "tool_result" -> toolCallInputs.getToolResult();
                default -> null;
            };
        }
        Object value = readProperty(inputs, snakeName);
        return value == null ? readProperty(inputs, camelName) : value;
    }

    private static Object defaultString(Object value) {
        return value == null ? "" : value;
    }

    private static String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Object readProperty(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String methodName : new String[] {"get" + suffix, name}) {
            try {
                Method method = findNoArgMethod(target.getClass(), methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException | SecurityException ignored) {
                // Preserve Python getattr-style tolerance for dynamic callback payloads.
            }
        }
        try {
            Field field = findField(target.getClass(), name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Preserve Python getattr-style tolerance for dynamic callback payloads.
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> targetType, String methodName) throws NoSuchMethodException {
        try {
            return targetType.getMethod(methodName);
        } catch (NoSuchMethodException exception) {
            return targetType.getDeclaredMethod(methodName);
        }
    }

    private static Field findField(Class<?> targetType, String fieldName) throws NoSuchFieldException {
        try {
            return targetType.getField(fieldName);
        } catch (NoSuchFieldException exception) {
            return targetType.getDeclaredField(fieldName);
        }
    }
}
