/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Mirrors Python's converter helper functions in
 * {@code openjiuwen/agent_evolving/agent_rl/online/rail/converter.py}.
 */
public final class TrajectoryConverterHelper {

    private TrajectoryConverterHelper() {
    }

    public static Object jsonValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>();
            for (Object item : collection) {
                result.add(jsonValue(item));
            }
            return result;
        }
        if (value.getClass().isArray()) {
            List<Object> result = new ArrayList<>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                result.add(jsonValue(Array.get(value, i)));
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, val) -> result.put(String.valueOf(key), jsonValue(val)));
            return result;
        }
        Map<String, Object> dumped = modelDump(value);
        if (dumped != null) {
            return jsonValue(dumped);
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> messageToDict(Object message) {
        if (message instanceof Map<?, ?>) {
            return (Map<String, Object>) jsonValue(message);
        }
        Map<String, Object> dumped = modelDump(message);
        if (dumped != null) {
            return (Map<String, Object>) jsonValue(dumped);
        }

        Object role = getAttribute(message, "role");
        if (role != null) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("role", String.valueOf(role));
            out.put("content", jsonValue(attributeOrDefault(message, "content", "")));

            Object name = getAttribute(message, "name");
            if (name != null) {
                out.put("name", String.valueOf(name));
            }
            Object metadata = getAttribute(message, "metadata");
            if (truthy(metadata)) {
                out.put("metadata", jsonValue(metadata));
            }
            Object toolCalls = firstTruthy(getAttribute(message, "tool_calls"), getAttribute(message, "toolCalls"));
            if (truthy(toolCalls)) {
                out.put("tool_calls", jsonValue(toolCalls));
            }
            return out;
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("role", "unknown");
        fallback.put("content", String.valueOf(message));
        return fallback;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> responseToDict(Object response) {
        if (response == null) {
            return new LinkedHashMap<>();
        }
        if (response instanceof Map<?, ?>) {
            return (Map<String, Object>) jsonValue(response);
        }
        Map<String, Object> dumped = modelDump(response);
        if (dumped != null) {
            return (Map<String, Object>) jsonValue(dumped);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", attributeOrDefault(response, "role", "assistant"));
        out.put("content", attributeOrDefault(response, "content", ""));

        Object toolCalls = firstTruthy(getAttribute(response, "tool_calls"), getAttribute(response, "toolCalls"));
        if (toolCalls != null) {
            out.put("tool_calls", jsonValue(toolCalls));
        }
        Object usage = firstTruthy(getAttribute(response, "usage_metadata"),
                getAttribute(response, "usageMetadata"),
                getAttribute(response, "usage"));
        if (usage != null) {
            out.put("usage", jsonValue(usage));
        }
        Object finishReason = firstTruthy(getAttribute(response, "finish_reason"),
                getAttribute(response, "finishReason"));
        if (finishReason != null) {
            out.put("finish_reason", finishReason);
        }
        Object reasoningContent = firstTruthy(getAttribute(response, "reasoning_content"),
                getAttribute(response, "reasoningContent"));
        if (reasoningContent != null) {
            out.put("reasoning_content", reasoningContent);
        }
        return out;
    }

    public static String extractText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String text && !text.isEmpty()) {
                    parts.add(text);
                } else if (item instanceof Map<?, ?> map) {
                    Object text = firstTruthy(map.get("text"), map.get("content"));
                    if (text instanceof String textValue && !textValue.isEmpty()) {
                        parts.add(textValue);
                    }
                }
            }
            return String.join("\n", parts);
        }
        return String.valueOf(value);
    }

    public static List<Double> coerceLogprobs(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            List<Double> out = new ArrayList<>();
            for (Object item : list) {
                Double number = toDouble(item);
                if (number != null) {
                    out.add(number);
                }
            }
            return out.isEmpty() ? null : out;
        }

        Object content = value instanceof Map<?, ?> map ? map.get("content") : getAttribute(value, "content");
        if (content instanceof List<?> list) {
            List<Double> out = new ArrayList<>();
            for (Object item : list) {
                Object raw = item instanceof Map<?, ?> map ? map.get("logprob") : getAttribute(item, "logprob");
                Double number = toDouble(raw);
                if (number != null) {
                    out.add(number);
                }
            }
            return out.isEmpty() ? null : out;
        }
        return null;
    }

    public static Map<String, Object> fingerprintPayload(List<Map<String, Object>> messages, Object tools) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messages", messages);
        payload.put("tools", jsonValue(tools));
        String raw = canonicalJson(payload);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "rail-local-sha256");
            result.put("sha256", hex.toString());
            return result;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static Object getAttribute(Object obj, String attrName) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map<?, ?> map) {
            Object direct = map.get(attrName);
            if (direct != null) {
                return direct;
            }
            return map.get(toCamel(attrName));
        }
        for (String getterName : getterNames(attrName)) {
            Method getter = findMethod(obj.getClass(), getterName);
            if (getter != null) {
                try {
                    getter.setAccessible(true);
                    return getter.invoke(obj);
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
        for (String fieldName : fieldNames(attrName)) {
            Field field = findField(obj.getClass(), fieldName);
            if (field != null) {
                try {
                    field.setAccessible(true);
                    return field.get(obj);
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public static Object firstTruthy(Object... values) {
        for (Object value : values) {
            if (truthy(value)) {
                return value;
            }
        }
        return null;
    }

    public static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return !text.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        return true;
    }

    private static Map<String, Object> modelDump(Object value) {
        if (value == null) {
            return null;
        }
        for (String methodName : List.of("model_dump", "modelDump")) {
            Method method = findMethod(value.getClass(), methodName);
            if (method == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object dumped = method.invoke(value);
                if (dumped instanceof Map<?, ?> map) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    map.forEach((key, val) -> out.put(String.valueOf(key), val));
                    return out;
                }
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object attributeOrDefault(Object obj, String attrName, Object defaultValue) {
        Object value = getAttribute(obj, attrName);
        return value != null ? value : defaultValue;
    }

    private static List<String> getterNames(String attrName) {
        String camel = toCamel(attrName);
        String pascal = capitalize(camel);
        return List.of("get" + capitalize(attrName), "is" + capitalize(attrName), "get" + pascal, "is" + pascal);
    }

    private static List<String> fieldNames(String attrName) {
        String camel = toCamel(attrName);
        return camel.equals(attrName) ? List.of(attrName) : List.of(attrName, camel);
    }

    private static String toCamel(String value) {
        StringBuilder out = new StringBuilder();
        boolean upperNext = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '_') {
                upperNext = true;
            } else if (upperNext) {
                out.append(Character.toUpperCase(ch));
                upperNext = false;
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private static Method findMethod(Class<?> type, String methodName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Double toDouble(Object obj) {
        if (obj instanceof Number number) {
            return number.doubleValue();
        }
        if (obj instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String canonicalJson(Object value) {
        Object json = jsonValue(value);
        if (json == null) {
            return "null";
        }
        if (json instanceof String text) {
            return "\"" + jsonEscape(text) + "\"";
        }
        if (json instanceof Number || json instanceof Boolean) {
            return String.valueOf(json);
        }
        if (json instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) {
                parts.add(canonicalJson(item));
            }
            return "[" + String.join(",", parts) + "]";
        }
        if (json instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, val) -> sorted.put(String.valueOf(key), val));
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                parts.add(canonicalJson(entry.getKey()) + ":" + canonicalJson(entry.getValue()));
            }
            return "{" + String.join(",", parts) + "}";
        }
        return "\"" + jsonEscape(String.valueOf(json)) + "\"";
    }

    private static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
