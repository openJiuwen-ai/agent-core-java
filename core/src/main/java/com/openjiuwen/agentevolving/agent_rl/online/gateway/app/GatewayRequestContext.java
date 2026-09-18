/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.gateway.app;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request validation helpers for gateway chat turns.
 * <p>
 * Mirrors Python's module helpers in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/app/request_context.py}.
 */
public final class GatewayRequestContext {

    private static final String SINGLE_USER_DEFAULT_ID = "jiuwenclaw-web";

    private GatewayRequestContext() {
    }

    public static String resolveTraceId(Map<String, ?> requestHeaders) {
        String traceId = findHeaderIgnoreCase(requestHeaders, "x-request-id");
        if (traceId != null && !traceId.isEmpty()) {
            return traceId;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public static List<?> requireMessages(Map<String, Object> body) {
        Object messages = body != null ? body.get("messages") : null;
        if (!(messages instanceof List<?> list) || list.isEmpty()) {
            throw new GatewayHttpException(400, "messages must be a non-empty list");
        }
        return list;
    }

    public static String requireUserId(Map<String, ?> requestHeaders, Object config) {
        String userId = stringValue(findHeaderIgnoreCase(requestHeaders, "x-user-id")).trim();
        if (userId.isEmpty() && pythonTruthy(readSingleUserDefaultFlag(config))) {
            userId = SINGLE_USER_DEFAULT_ID;
        }
        if (userId.isEmpty()) {
            throw new GatewayHttpException(
                    400,
                    "missing x-user-id header; online training requires a stable user id"
            );
        }
        return userId;
    }

    private static String findHeaderIgnoreCase(Map<String, ?> headers, String headerName) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, ?> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(headerName)) {
                return entry.getValue() == null ? null : String.valueOf(entry.getValue());
            }
        }
        return null;
    }

    private static Object readSingleUserDefaultFlag(Object config) {
        if (config == null) {
            return null;
        }
        if (config instanceof Map<?, ?> map) {
            if (map.containsKey("single_user_default")) {
                return map.get("single_user_default");
            }
            if (map.containsKey("singleUserDefault")) {
                return map.get("singleUserDefault");
            }
        }
        for (String methodName : List.of("singleUserDefault", "isSingleUserDefault", "getSingleUserDefault")) {
            try {
                Method method = config.getClass().getMethod(methodName);
                return method.invoke(config);
            } catch (ReflectiveOperationException ignored) {
                // Keep probing alternate access patterns used by translated configs.
            }
        }
        for (String fieldName : List.of("singleUserDefault", "single_user_default")) {
            try {
                Field field = config.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(config);
            } catch (ReflectiveOperationException ignored) {
                // Keep probing alternate access patterns used by translated configs.
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean pythonTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence chars) {
            return !chars.isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }
}
