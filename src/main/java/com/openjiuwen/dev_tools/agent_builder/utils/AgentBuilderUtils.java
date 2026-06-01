/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import com.openjiuwen.core.common.security.JsonUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent builder utility methods.
 * <p>
 * Mirrors Python's {@code utils} in
 * {@code openjiuwen.dev_tools.agent_builder.utils.utils}.
 */
public final class AgentBuilderUtils {

    private AgentBuilderUtils() {
    }

    /** Extract JSON from a Markdown code block, or return the original text. */
    public static String extractJsonFromText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = Pattern.compile(AgentBuilderConstants.JSON_EXTRACT_PATTERN).matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return text;
    }

    /** Format dialog history as "role: content" lines. */
    public static String formatDialogHistory(List<Map<String, Object>> dialogHistory) {
        return formatDialogHistory(dialogHistory, "\n");
    }

    public static String formatDialogHistory(List<Map<String, Object>> dialogHistory, String separator) {
        if (dialogHistory == null || dialogHistory.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> message : dialogHistory) {
            Object role = message.getOrDefault("role", "unknown");
            Object content = message.getOrDefault("content", "");
            lines.add(role + ": " + content);
        }
        return String.join(separator, lines);
    }

    /** Safely parse JSON, returning the default on blank input or parse failure. */
    public static Object safeJsonLoads(String text) {
        return safeJsonLoads(text, null);
    }

    /** Safely parse JSON, returning the default on blank input or parse failure. */
    public static Object safeJsonLoads(String text, Object defaultValue) {
        if (text == null || text.isEmpty()) {
            return defaultValue;
        }
        try {
            return JsonUtils.safeJsonLoads(text, Object.class, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /** Validate session ids allowed by Python: letters, numbers, underscore and hyphen. */
    public static boolean validateSessionId(String sessionId) {
        return sessionId != null && sessionId.matches("^[a-zA-Z0-9_-]+$");
    }

    /** Merge two lists of dicts by key. */
    public static List<Map<String, Object>> mergeDictLists(List<Map<String, Object>> a,
                                                            List<Map<String, Object>> b,
                                                            String keyField) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<Object> existingKeys = new LinkedHashSet<>();
        if (a != null) {
            for (Map<String, Object> item : a) {
                result.add(item);
                Object key = item.get(keyField);
                if (key != null) {
                    existingKeys.add(key);
                }
            }
        }
        if (b == null || b.isEmpty()) {
            return result;
        }

        for (Map<String, Object> item : b) {
            Object key = item.get(keyField);
            if (key != null && !existingKeys.contains(key)) {
                result.add(item);
                existingKeys.add(key);
            }
        }
        return result;
    }

    /** Deep merge dictionaries without mutating the base map. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepMergeDict(Map<String, Object> base, Map<String, Object> update) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (base != null) {
            result.putAll(base);
        }
        if (update == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : update.entrySet()) {
            Object current = result.get(entry.getKey());
            Object next = entry.getValue();
            if (current instanceof Map<?, ?> currentMap && next instanceof Map<?, ?> nextMap) {
                result.put(entry.getKey(), deepMergeDict(
                        (Map<String, Object>) currentMap,
                        (Map<String, Object>) nextMap));
            } else {
                result.put(entry.getKey(), next);
            }
        }
        return result;
    }
}
