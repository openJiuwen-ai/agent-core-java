/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

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
 * @since 1.0
 */
public record HandoffSignal(String target, String message, String reason) {

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String HANDOFF_TARGET_KEY = "__handoff_to__";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String HANDOFF_MESSAGE_KEY = "__handoff_message__";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String HANDOFF_REASON_KEY = "__handoff_reason__";

    private static final String DEFAULT_CONTEXT_ID = "default_context_id";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * Auto-generated for codecheck compliance.
     */
    public static HandoffSignal extract(Object result) {
        return extract(result, null);
    }

    /**
     * Auto-generated for codecheck compliance.
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

    /**
     * Recover a handoff payload from the agent session's message history.
     *
     * <p>When the LLM emits a {@code transfer_to_xxx} tool call and then
     * produces a follow-up text message, the final result dict no longer
     * carries {@link #HANDOFF_TARGET_KEY}. Python's
     * {@code _find_handoff_from_session} walks the session's tool messages
     * to recover the signal; this method mirrors that fallback so the
     * handoff chain continues into the target agent instead of terminating
     * on the triage agent's acknowledgement text.</p>
     */
    @SuppressWarnings("unchecked")
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
     * Parse a tool message's content into a handoff payload map.
     *
     * <p>Java tools store their result via {@code String.valueOf(result)},
     * producing a Java map literal such as
     * {@code {__handoff_to__=billing_support, __handoff_message__=, ...}}.
     * Python and some Java tools emit JSON instead. This helper accepts both
     * shapes so parity holds regardless of which tool implementation produced
     * the message.</p>
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
    private static Map<String, Object> toStrMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static Map<String, Object> tryParseJson(String text) {
        try {
            Object parsed = JSON_MAPPER.readValue(text, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                return toStrMap(map);
            }
        } catch (Exception e) {
            // Not JSON; fall through to Java map literal parsing.
        }
        return null;
    }

    /**
     * Parse Java's {@code Map.toString()} output such as
     * {@code {__handoff_to__=billing_support, __handoff_reason__=用户质疑...}}.
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

    private static String normalize(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text;
    }
}
