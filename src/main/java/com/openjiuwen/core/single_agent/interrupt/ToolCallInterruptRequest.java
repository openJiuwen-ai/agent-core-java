/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.interrupt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code ToolCallInterruptRequest} in
 * {@code openjiuwen/core/single_agent/interrupt/response.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCallInterruptRequest extends InterruptRequest {
    @JsonProperty("tool_name")
    private String toolName = "";

    @JsonProperty("tool_call_id")
    private String toolCallId = "";

    @JsonProperty("tool_args")
    private Object toolArgs;

    @JsonProperty("index")
    private Integer index;

    public ToolCallInterruptRequest() {
    }

    public static ToolCallInterruptRequest fromToolCall(InterruptRequest request, Object toolCall) {
        ToolCallInterruptRequest result = new ToolCallInterruptRequest();
        Map<String, Object> baseFields = request == null ? new LinkedHashMap<>() : request.toMap();

        result.setMessage((String) baseFields.getOrDefault("message", ""));
        result.setPayloadSchema(castMap(baseFields.get("payload_schema")));
        result.setAutoConfirmKey((String) baseFields.getOrDefault("auto_confirm_key", ""));
        result.setUiOptions(castList(baseFields.get("ui_options")));

        for (Map.Entry<String, Object> entry : baseFields.entrySet()) {
            if (!isBaseField(entry.getKey())) {
                result.putExtraField(entry.getKey(), entry.getValue());
            }
        }

        result.setToolName(stringValue(readAttribute(toolCall, "name"), toolCall));
        result.setToolCallId(stringValue(readAttribute(toolCall, "id"), ""));
        result.setToolArgs(readAttribute(toolCall, "arguments"));
        Object indexValue = readAttribute(toolCall, "index");
        if (indexValue instanceof Number) {
            result.setIndex(((Number) indexValue).intValue());
        }
        return result;
    }

    private static boolean isBaseField(String key) {
        return "message".equals(key)
                || "payload_schema".equals(key)
                || "auto_confirm_key".equals(key)
                || "ui_options".equals(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Map<String, Object>> castList(Object value) {
        if (value instanceof java.util.List<?>) {
            return (java.util.List<Map<String, Object>>) value;
        }
        return null;
    }

    private static Object readAttribute(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String methodName : new String[]{name, "get" + capitalized}) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        try {
            Field field = target.getClass().getField(name);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String stringValue(Object value, Object fallback) {
        if (value == null) {
            return fallback == null ? "" : String.valueOf(fallback);
        }
        return String.valueOf(value);
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName == null ? "" : toolName;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId == null ? "" : toolCallId;
    }

    public Object getToolArgs() {
        return toolArgs;
    }

    public void setToolArgs(Object toolArgs) {
        this.toolArgs = toolArgs;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }
}
