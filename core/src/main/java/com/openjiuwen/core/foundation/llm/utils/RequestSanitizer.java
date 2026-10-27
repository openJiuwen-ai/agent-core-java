/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.utils;

import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Non-mutating normalization at JSON provider request boundaries.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.foundation.llm.utils.request_sanitizer}.
 * Preserves valid UTF-16 surrogate pairs; replaces isolated halves with U+FFFD
 * so {@code BodyPublishers.ofString(..., UTF_8)} does not silently emit {@code '?'}.</p>
 *
 * @since 0.1.18
 */
public final class RequestSanitizer {
    private static final char REPLACEMENT = '\uFFFD';

    private RequestSanitizer() {
    }

    /**
     * Recursively clean strings in a request payload.
     *
     * @param value request fragment
     * @return cleaned copy (maps/lists cloned; other values returned as-is)
     * @since 0.1.18
     */
    public static Object cleanUnicode(Object value) {
        if (value instanceof String text) {
            return cleanString(text);
        }
        if (value instanceof Map<?, ?> map) {
            return cleanMap(map);
        }
        if (value instanceof List<?> list) {
            return cleanList(list);
        }
        return value;
    }

    /**
     * Sanitize chat-completion request params before JSON serialization.
     *
     * @param params request body params
     * @return cleaned params map; empty map when {@code params} is null
     * @since 0.1.18
     */
    public static Map<String, Object> sanitizeChatRequest(Map<String, Object> params) {
        if (params == null) {
            return Map.of();
        }
        Object cleaned = cleanUnicode(params);
        if (!(cleaned instanceof Map<?, ?> map)) {
            return params;
        }
        Map<String, Object> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    static String cleanString(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (!hasLoneSurrogate(text)) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.isHighSurrogate(current)) {
                appendHighSurrogate(builder, text, i);
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    i++;
                }
            } else if (Character.isLowSurrogate(current)) {
                builder.append(REPLACEMENT);
            } else {
                builder.append(current);
            }
        }
        Loggers.LLM.warning("Replaced lone UTF-16 surrogates with U+FFFD before model request encoding");
        return builder.toString();
    }

    private static Map<String, Object> cleanMap(Map<?, ?> map) {
        Map<String, Object> cleaned = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey() == null ? null : cleanString(String.valueOf(entry.getKey()));
            cleaned.put(key, cleanUnicode(entry.getValue()));
        }
        return cleaned;
    }

    private static List<Object> cleanList(List<?> list) {
        List<Object> cleaned = new ArrayList<>(list.size());
        for (Object item : list) {
            cleaned.add(cleanUnicode(item));
        }
        return cleaned;
    }

    private static boolean hasLoneSurrogate(String text) {
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) {
                    return true;
                }
                i++;
                continue;
            }
            if (Character.isLowSurrogate(current)) {
                return true;
            }
        }
        return false;
    }

    private static void appendHighSurrogate(StringBuilder builder, String text, int index) {
        if (index + 1 < text.length() && Character.isLowSurrogate(text.charAt(index + 1))) {
            builder.append(text.charAt(index)).append(text.charAt(index + 1));
            return;
        }
        builder.append(REPLACEMENT);
    }
}
