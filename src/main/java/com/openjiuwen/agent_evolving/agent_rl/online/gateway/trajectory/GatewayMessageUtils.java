/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import java.util.List;
import java.util.Map;

/**
 * Message helpers used by gateway trajectory runtime.
 * <p>
 * Mirrors Python's helpers in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.message_utils}.
 */
public final class GatewayMessageUtils {

    private GatewayMessageUtils() {
    }

    public static String flattenMessageContent(Object content) {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && "text".equals(String.valueOf(map.get("type")))) {
                    Object text = map.get("text");
                    if (text != null && !String.valueOf(text).isBlank()) {
                        if (!builder.isEmpty()) {
                            builder.append(' ');
                        }
                        builder.append(text);
                    }
                }
            }
            return builder.toString().trim();
        }
        return content == null ? "" : String.valueOf(content);
    }

    public static String extractLastUserInstruction(List<Map<String, Object>> messages) {
        if (messages == null) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> message = messages.get(i);
            if (message != null && "user".equals(message.get("role"))) {
                String text = flattenMessageContent(message.get("content"));
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }
}
