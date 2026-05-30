// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Convert Rail-collected trajectories into online RL rail-v1 batches.
 * <p>
 * Mirrors Python helpers in
 * {@code openjiuwen.agent_evolving.agent_rl.online.rail.converter}.
 */
public final class TrajectoryConverterHelper {

    private TrajectoryConverterHelper() {
    }

    private static Map<String, Object> modelDump(Object value) {
        if (value == null) {
            return null;
        }
        for (String methodName : List.of("model_dump", "modelDump")) {
            try {
                Method method = value.getClass().getMethod(methodName);
                Object dumped = method.invoke(value);
                if (dumped instanceof Map<?, ?> map) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    map.forEach((key, val) -> out.put(String.valueOf(key), val));
                    return out;
                }
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Convert any value to a JSON-safe value.
     */
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

    /**
     * Convert a chat message object to a map.
     */
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
            out.put("content", jsonValue(getAttributeOrDefault(message, "content", "")));

            Object name = getAttribute(message, "name");
            if (name != null) {
                out.put("name", String.valueOf(name));
            }
            Object metadata = getAttribute(message, "metadata");
            if (truthy(metadata)) {
                out.put("metadata", jsonValue(metadata));
            }
            Object toolCalls = getAttribute(message, "tool_calls");
            if (truthy(toolCalls)) {
                out.put("tool_calls", jsonValue(toolCalls));
            }
            return out;
        }
        return Map.of("role", "unknown", "content", String.valueOf(message));
    }

    /**
     * Convert an LLM response object to a map.
     */
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
        out.put("role", getAttributeOrDefault(response, "role", "assistant"));
        out.put("content", getAttributeOrDefault(response, "content", ""));

        Object toolCalls = getAttribute(response, "tool_calls");
        if (toolCalls != null) {
            out.put("tool_calls", jsonValue(toolCalls));
        }

        Object usage = firstTruthy(getAttribute(response, "usage_metadata"), getAttribute(response, "usage"));
        if (usage != null) {
            out.put("usage", jsonValue(usage));
        }

        Object finishReason = getAttribute(response, "finish_reason");
        if (finishReason != null) {
            out.put("finish_reason", finishReason);
        }

        Object reasoningContent = getAttribute(response, "reasoning_content");
        if (reasoningContent != null) {
            out.put("reasoning_content", reasoningContent);
        }
        return out;
    }

    /**
     * Extract display text from content.
     */
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

    /**
     * Coerce logprobs to a list of floats.
     */
    public static List<Double> coerceLogprobs(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            List<Double> out = new ArrayList<>();
            for (Object item : list) {
                try {
                    out.add(toDouble(item));
                } catch (RuntimeException ignored) {
                }
            }
            return out.isEmpty() ? null : out;
        }

        Object content = value instanceof Map<?, ?> map ? map.get("content") : getAttribute(value, "content");
        if (content instanceof List<?> list) {
            List<Double> out = new ArrayList<>();
            for (Object item : list) {
                try {
                    Object logprob = item instanceof Map<?, ?> map ? map.get("logprob") : getAttribute(item, "logprob");
                    out.add(toDouble(logprob));
                } catch (RuntimeException ignored) {
                }
            }
            return out.isEmpty() ? null : out;
        }
        return null;
    }

    /**
     * Create a stable fingerprint for rendered messages and tools.
     */
    public static Map<String, Object> fingerprintPayload(List<Map<String, Object>> messages, Object tools) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messages", messages);
            payload.put("tools", jsonValue(tools));
            String raw = canonicalJson(payload);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "rail-local-sha256");
            result.put("sha256", hex.toString());
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to fingerprint trajectory payload", exception);
        }
    }

    public static Object getAttribute(Object obj, String attrName) {
        if (obj == null) {
            return null;
        }
        for (String getterName : getterNames(attrName)) {
            try {
                Method getter = obj.getClass().getMethod(getterName);
                return getter.invoke(obj);
            } catch (Exception ignored) {
            }
        }
        for (String fieldName : fieldNames(attrName)) {
            Class<?> type = obj.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(obj);
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                } catch (Exception ignored) {
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

    private static Object getAttributeOrDefault(Object obj, String attrName, Object defaultValue) {
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

    private static double toDouble(Object obj) {
        if (obj == null) {
            throw new NumberFormatException("null");
        }
        if (obj instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(obj.toString());
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
