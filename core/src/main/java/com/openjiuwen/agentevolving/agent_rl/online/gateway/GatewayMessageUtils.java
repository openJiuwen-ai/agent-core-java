/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Message helpers used by gateway runtime.
 * <p>
 * Mirrors Python's module helpers in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/message_utils.py}.
 */
public final class GatewayMessageUtils {

    private GatewayMessageUtils() {
    }

    public static String flattenMessageContent(Object content) {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof List<?> items) {
            List<String> parts = new ArrayList<>();
            for (Object item : items) {
                if (item instanceof Map<?, ?> map && "text".equals(String.valueOf(map.get("type")))) {
                    Object text = map.get("text");
                    parts.add(text == null ? "" : String.valueOf(text));
                }
            }
            return String.join(" ", parts).trim();
        }
        if (content == null) {
            return "";
        }
        return String.valueOf(content);
    }

    public static String extractLastUserInstruction(List<?> messages) {
        if (messages == null) {
            return "";
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            Object message = messages.get(index);
            if (message instanceof Map<?, ?> map && "user".equals(String.valueOf(map.get("role")))) {
                String text = flattenMessageContent(map.get("content"));
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }
}
