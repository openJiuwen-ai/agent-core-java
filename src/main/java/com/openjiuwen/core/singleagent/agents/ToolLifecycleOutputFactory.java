/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.AbilityManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class ToolLifecycleOutputFactory {

    private ToolLifecycleOutputFactory() {
    }

    static void ensureToolCallId(ToolCall toolCall) {
        if (toolCall == null) {
            return;
        }
        if (toolCall.getId() == null || toolCall.getId().isBlank()) {
            toolCall.setId(UUID.randomUUID().toString());
        }
    }

    static OutputSchema buildToolCallOutput(ToolCall toolCall, int index) {
        ensureToolCallId(toolCall);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool_call_id", toolCall != null ? toolCall.getId() : null);
        payload.put("tool_name", toolCall != null ? toolCall.getName() : null);
        payload.put("arguments", stringify(toolCall != null ? toolCall.getArguments() : null));
        return new OutputSchema("tool_call", index, payload);
    }

    static OutputSchema buildToolResultOutput(ToolCall toolCall,
                                              AbilityManager.ExecutionResult result,
                                              int index) {
        ensureToolCallId(toolCall);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool_call_id", toolCall != null ? toolCall.getId() : null);
        payload.put("tool_name", toolCall != null ? toolCall.getName() : null);
        if (isErrorResult(result)) {
            payload.put("status", "error");
            payload.put("error", resolveErrorValue(result));
        } else {
            payload.put("status", "completed");
            payload.put("result", stringify(result != null ? result.result() : null));
        }
        return new OutputSchema("tool_result", index, payload);
    }

    private static boolean isErrorResult(AbilityManager.ExecutionResult result) {
        if (result == null) {
            return true;
        }
        Object success = attribute(result.result(), "success");
        if (Boolean.FALSE.equals(success)) {
            return true;
        }
        String message = toolMessageContent(result);
        return result.result() == null && !message.isBlank()
                || message.startsWith("Invalid tool arguments JSON:")
                || message.startsWith("Ability execution error:");
    }

    private static String resolveErrorValue(AbilityManager.ExecutionResult result) {
        if (result == null) {
            return "Tool execution failed";
        }
        Object error = attribute(result.result(), "error");
        if (error != null && !String.valueOf(error).isBlank()) {
            return stringify(error);
        }
        String message = toolMessageContent(result);
        return !message.isBlank() ? message : "Tool execution failed";
    }

    private static String toolMessageContent(AbilityManager.ExecutionResult result) {
        ToolMessage toolMessage = result != null ? result.toolMessage() : null;
        Object content = toolMessage != null ? toolMessage.getContent() : null;
        return content == null ? "" : String.valueOf(content);
    }

    private static String stringify(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static Object attribute(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            Method method = target.getClass().getMethod(getter);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            try {
                Field field = target.getClass().getField(name);
                return field.get(target);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }
}
