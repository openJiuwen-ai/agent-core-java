/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Module helpers for MCP tools.
 *
 * <p>Mirrors Python's module constants and {@code extract_mcp_tool_result_content} in
 * {@code openjiuwen/core/foundation/tool/mcp/base.py}.</p>
 */
public final class McpBase {

    public static final float NO_TIMEOUT = -1.0f;

    private McpBase() {
    }

    public static Object extractMcpToolResultContent(Object toolResult) {
        Object content = attribute(toolResult, "content");
        if (!(content instanceof List<?> contentItems) || contentItems.isEmpty()) {
            return null;
        }
        Object item = contentItems.get(contentItems.size() - 1);
        Object text = attribute(item, "text");
        if (text != null) {
            return text;
        }

        Object mimeType = attribute(item, "mimeType");
        if (mimeType == null) {
            mimeType = attribute(item, "mime_type");
        }
        Object data = attribute(item, "data");
        if (data != null) {
            if (mimeType != null && String.valueOf(mimeType).startsWith("image/")) {
                return "[image content: " + mimeType + ", " + String.valueOf(data).length() + " base64 chars]";
            }
            return data;
        }

        Object dumped = invokeNoArgs(item, "model_dump");
        if (dumped == null) {
            dumped = invokeNoArgs(item, "modelDump");
        }
        if (dumped instanceof Map<?, ?> dumpedMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            dumpedMap.forEach((key, value) -> {
                if (!"data".equals(String.valueOf(key)) && value != null) {
                    result.put(String.valueOf(key), value);
                }
            });
            return result;
        }
        return String.valueOf(item);
    }

    private static Object attribute(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        Object value = invokeNoArgs(target, name);
        if (value != null) {
            return value;
        }
        value = invokeNoArgs(target, "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
        if (value != null) {
            return value;
        }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeNoArgs(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
