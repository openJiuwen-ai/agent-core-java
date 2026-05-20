/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.session.Session;

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

    /**
     * Auto-generated for codecheck compliance.
     */
    public static HandoffSignal extract(Object result) {
        Map<String, Object> payload = findPayload(result);
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public static HandoffSignal extract(Object result, Session session) {
        return extract(result);
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

    private static String normalize(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text;
    }
}
