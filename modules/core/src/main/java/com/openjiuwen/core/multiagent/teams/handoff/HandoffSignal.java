/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.Session;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public record HandoffSignal used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public record HandoffSignal(String target, String message, String reason) {
    /**
     * HANDOFF_TARGET_KEY.
     * 
     * @since 0.1.7
     */
    public static final String HANDOFF_TARGET_KEY = "__handoff_to__";

    /**
     * HANDOFF_MESSAGE_KEY.
     * 
     * @since 0.1.7
     */
    public static final String HANDOFF_MESSAGE_KEY = "__handoff_message__";

    /**
     * HANDOFF_REASON_KEY.
     * 
     * @since 0.1.7
     */
    public static final String HANDOFF_REASON_KEY = "__handoff_reason__";

    private static final String DEFAULT_CONTEXT_ID = "default_context_id";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * extract.
     * 
     * @param result result
     * @return the result
     * @since 0.1.7
     */
    public static HandoffSignal extract(Object result) {
        return extract(result, null);
    }

    /**
     * extract.
     * 
     * @param result result
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    public static HandoffSignal extract(Object result, Session session) {
        Map<String, Object> payload = findPayload(result);
        if (payload.isEmpty() && session != null) {
            payload = findHandoffFromSession(session);
        }
        if (payload.isEmpty()) {
            return null;
        }
        Object target = payload.get(HANDOFF_TARGET_KEY);
        if (!(target instanceof String targetId) || targetId.isBlank()) {
            return null;
        }
        String message = normalize(payload.get(HANDOFF_MESSAGE_KEY));
        String reason = normalize(payload.get(HANDOFF_REASON_KEY));
        return new HandoffSignal(targetId, message, reason);
    }

    @SuppressWarnings("unchecked")
    /**
     * findPayload.
     * 
     * @param result result
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> findPayload(Object result) {
        if (!(result instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        if (raw.containsKey(HANDOFF_TARGET_KEY)) {
            return (Map<String, Object>) raw;
        }
        for (String key : new String[]{"output", "result", "content"}) {
            Object nested = raw.get(key);
            if (nested instanceof Map<?, ?> nestedMap && nestedMap.containsKey(HANDOFF_TARGET_KEY)) {
                return (Map<String, Object>) nestedMap;
            }
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    /**
     * findHandoffFromSession.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> findHandoffFromSession(Session session) {
        if (session == null) {
            return Collections.emptyMap();
        }
        Object rawStates = session.getState("context");
        if (!(rawStates instanceof Map<?, ?> rawStateMap)) {
            return Collections.emptyMap();
        }
        Object rawContext = rawStateMap.get(DEFAULT_CONTEXT_ID);
        if (!(rawContext instanceof Map<?, ?> rawCtxMap)) {
            return Collections.emptyMap();
        }
        Object rawMessages = rawCtxMap.get("messages");
        if (!(rawMessages instanceof List<?> messages)) {
            return Collections.emptyMap();
        }
        // Walk in reverse so the most recent handoff tool result wins.
        for (int i = messages.size() - 1; i >= 0; i--) {
            Object item = messages.get(i);
            if (!(item instanceof BaseMessage message)) {
                continue;
            }
            if (!"tool".equals(message.getRole())) {
                continue;
            }
            Object content = message.getContent();
            if (content == null) {
                continue;
            }
            Map<String, Object> parsed = parseHandoffContent(content);
            if (parsed != null) {
                return parsed;
            }
        }
        return Collections.emptyMap();
    }

    /**
     * parseHandoffContent.
     * 
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> parseHandoffContent(Object content) {
        if (content instanceof Map<?, ?> map && map.containsKey(HANDOFF_TARGET_KEY)) {
            return toStrMap(map);
        }
        if (!(content instanceof String text) || text.isBlank()) {
            return null;
        }
        Map<String, Object> parsed = tryParseJson(text);
        if (parsed != null && parsed.containsKey(HANDOFF_TARGET_KEY)) {
            return parsed;
        }
        Map<String, Object> javaMap = tryParseJavaMapString(text);
        if (javaMap != null && javaMap.containsKey(HANDOFF_TARGET_KEY)) {
            return javaMap;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    /**
     * toStrMap.
     * 
     * @param map map
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> toStrMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /**
     * tryParseJson.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> tryParseJson(String text) {
        try {
            Object parsed = JSON_MAPPER.readValue(text, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                return toStrMap(map);
            }
        } catch (JsonProcessingException e) {
            // Not JSON; fall through to Java map literal parsing.
        }
        return null;
    }

    /**
     * Parse Java's {@code Map.toString()} output such as
     * {@code {__handoff_to__=billing_support, __handoff_reason__=用户质疑...}}.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> tryParseJavaMapString(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }
        String body = trimmed.substring(1, trimmed.length() - 1);
        if (body.isBlank()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();
        StringBuilder current = key;
        boolean inValue = false;
        int depth = 0;
        boolean escaped = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (!inValue) {
                if (c == '=') {
                    inValue = true;
                    current = value;
                    continue;
                }
                key.append(c);
                continue;
            }
            if (c == '{' || c == '[' || c == '(') {
                depth++;
                current.append(c);
                continue;
            }
            if (c == '}' || c == ']' || c == ')') {
                depth--;
                current.append(c);
                continue;
            }
            if (c == ',' && depth == 0) {
                putEntry(result, key.toString(), value.toString());
                key.setLength(0);
                value.setLength(0);
                inValue = false;
                current = key;
                continue;
            }
            current.append(c);
        }
        if (key.length() > 0 || inValue) {
            putEntry(result, key.toString(), value.toString());
        }
        return result;
    }

    /**
     * putEntry.
     * 
     * @param result result
     * @param rawKey rawKey
     * @param rawValue rawValue
     * @since 0.1.7
     */
    private static void putEntry(Map<String, Object> result, String rawKey, String rawValue) {
        String key = rawKey.strip();
        String value = rawValue.strip();
        if (key.isEmpty()) {
            return;
        }
        if ("null".equals(value) || value.isEmpty()) {
            result.put(key, null);
        } else {
            result.put(key, value);
        }
    }

    /**
     * normalize.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String normalize(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text;
    }
}
