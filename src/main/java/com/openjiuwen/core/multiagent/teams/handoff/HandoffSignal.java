/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.session.Session;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable handoff directive.
 * <p>
 * Mirrors Python's {@code HandoffSignal} in 
 * {@code openjiuwen.core.multi_agent.teams.handoff.handoff_signal}.
 * <p>
 * Produced by extract_handoff_signal function.
 * <p>
 * Attributes:
 * <ul>
 *     <li>target: ID of the target agent</li>
 *     <li>message: Optional context message forwarded to the target agent</li>
 *     <li>reason: Optional human-readable reason for the handoff</li>
 * </ul>
 */
public final class HandoffSignal {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_CONTEXT_ID = "default_context_id";
    
    public static final String HANDOFF_TARGET_KEY = "__handoff_to__";
    public static final String HANDOFF_MESSAGE_KEY = "__handoff_message__";
    public static final String HANDOFF_REASON_KEY = "__handoff_reason__";
    
    private final String target;
    private final String message;
    private final String reason;
    
    public HandoffSignal(String target) {
        this(target, null, null);
    }
    
    public HandoffSignal(String target, String message, String reason) {
        this.target = target;
        this.message = message;
        this.reason = reason;
    }
    
    public String getTarget() { return target; }
    public Optional<String> getMessage() { return Optional.ofNullable(message); }
    public Optional<String> getReason() { return Optional.ofNullable(reason); }

    /**
     * Search a result object for a handoff payload.
     *
     * @param result agent result
     * @return payload map when found
     */
    @SuppressWarnings("unchecked")
    public static Optional<Map<String, Object>> findHandoffPayload(Object result) {
        if (!(result instanceof Map<?, ?> raw)) {
            return Optional.empty();
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        if (map.containsKey(HANDOFF_TARGET_KEY)) {
            return Optional.of(map);
        }
        for (String key : List.of("output", "result", "content")) {
            Object nested = map.get(key);
            if (nested instanceof Map<?, ?> nestedRaw) {
                Map<String, Object> nestedMap = (Map<String, Object>) nestedRaw;
                if (nestedMap.containsKey(HANDOFF_TARGET_KEY)) {
                    return Optional.of(nestedMap);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Extract a handoff signal from a result object.
     *
     * @param result agent result
     * @return signal when present
     */
    public static Optional<HandoffSignal> extractHandoffSignal(Object result) {
        return extractHandoffSignal(result, null);
    }

    /**
     * Extract a handoff signal from result or, if absent, from agent session context.
     *
     * @param result agent result
     * @param agentSession agent session with context history
     * @return signal when present
     */
    public static Optional<HandoffSignal> extractHandoffSignal(Object result, Session agentSession) {
        Optional<Map<String, Object>> payload = findHandoffPayload(result);
        if (payload.isEmpty() && agentSession != null) {
            payload = findHandoffFromSession(agentSession);
        }
        return payload.flatMap(HandoffSignal::fromPayload);
    }

    private static Optional<HandoffSignal> fromPayload(Map<String, Object> payload) {
        Object targetValue = payload.get(HANDOFF_TARGET_KEY);
        if (!(targetValue instanceof String target) || target.isEmpty()) {
            return Optional.empty();
        }
        String message = nonEmptyString(payload.get(HANDOFF_MESSAGE_KEY));
        String reason = nonEmptyString(payload.get(HANDOFF_REASON_KEY));
        return Optional.of(new HandoffSignal(target, message, reason));
    }

    private static String nonEmptyString(Object value) {
        if (!(value instanceof String text) || text.isEmpty()) {
            return null;
        }
        return text;
    }

    /**
     * Find the latest handoff payload in a session's tool-message history.
     *
     * @param agentSession agent session
     * @return payload map when found
     */
    @SuppressWarnings("unchecked")
    public static Optional<Map<String, Object>> findHandoffFromSession(Session agentSession) {
        if (agentSession == null) {
            return Optional.empty();
        }
        Object ctxState = agentSession.getState("context");
        if (!(ctxState instanceof Map<?, ?> ctxMap)) {
            return Optional.empty();
        }
        Object defaultContext = ctxMap.get(DEFAULT_CONTEXT_ID);
        if (!(defaultContext instanceof Map<?, ?> defaultMap)) {
            return Optional.empty();
        }
        Object messagesValue = defaultMap.get("messages");
        if (!(messagesValue instanceof List<?> messages) || messages.isEmpty()) {
            return Optional.empty();
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Object message = messages.get(i);
            if (!"tool".equals(readMessageProperty(message, "role"))) {
                continue;
            }
            Object contentValue = readMessageProperty(message, "content");
            if (!(contentValue instanceof String content) || content.isEmpty()) {
                continue;
            }
            Map<String, Object> parsed = parsePayloadContent(content);
            if (parsed != null && parsed.containsKey(HANDOFF_TARGET_KEY)) {
                return Optional.of(parsed);
            }
        }
        return Optional.empty();
    }

    private static Object readMessageProperty(Object message, String propertyName) {
        if (message == null) {
            return null;
        }
        if (message instanceof Map<?, ?> map) {
            return map.get(propertyName);
        }
        String accessor = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        try {
            Method method = message.getClass().getMethod(accessor);
            return method.invoke(message);
        } catch (ReflectiveOperationException ignored) {
            try {
                Field field = message.getClass().getDeclaredField(propertyName);
                field.setAccessible(true);
                return field.get(message);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    private static Map<String, Object> parsePayloadContent(String content) {
        Map<String, Object> parsed = parseJsonMap(content);
        if (parsed != null) {
            return parsed;
        }
        return parseJsonMap(toJsonLikePythonDict(content));
    }

    private static Map<String, Object> parseJsonMap(String content) {
        try {
            return OBJECT_MAPPER.readValue(content, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String toJsonLikePythonDict(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("'", "\"")
                .replace(": None", ": null")
                .replace(": True", ": true")
                .replace(": False", ": false");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HandoffSignal that)) {
            return false;
        }
        return Objects.equals(target, that.target)
                && Objects.equals(message, that.message)
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, message, reason);
    }
    
    @Override
    public String toString() {
        return String.format("HandoffSignal(target=%s, message=%s, reason=%s)", 
                             target, message, reason);
    }
}
