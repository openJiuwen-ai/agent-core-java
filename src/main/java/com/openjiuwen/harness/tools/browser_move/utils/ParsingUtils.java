/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared parsing helpers for runtime/controller responses.
 *
 * <p>Mirrors Python's
 * {@code openjiuwen/harness/tools/browser_move/utils/parsing.py}.
 */
public final class ParsingUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> UNSUPPORTED_SCHEMA_KEYS = Set.of(
            "$schema", "$id", "$defs", "definitions", "$comment", "$anchor", "$vocabulary"
    );

    private ParsingUtils() {
    }

    public static Object sanitizeJsonSchema(Object schema) {
        if (!(schema instanceof Map<?, ?> rawMap)) {
            return schema;
        }

        Map<String, Object> working = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String key) {
                working.put(key, entry.getValue());
            }
        }

        for (String keyword : List.of("anyOf", "oneOf")) {
            Object variants = working.get(keyword);
            if (variants instanceof List<?> variantList && variantList.size() == 2) {
                List<Map<String, Object>> nonNull = variantList.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> castMap((Map<?, ?>) item))
                        .filter(item -> !"null".equals(item.get("type")))
                        .toList();
                long nullCount = variantList.size() - nonNull.size();
                if (nullCount == 1 && nonNull.size() == 1) {
                    Map<String, Object> merged = new LinkedHashMap<>(working);
                    merged.remove(keyword);
                    merged.putAll(nonNull.get(0));
                    working = merged;
                }
            }
        }

        Map<String, Object> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : working.entrySet()) {
            if (!UNSUPPORTED_SCHEMA_KEYS.contains(entry.getKey())) {
                cleaned.put(entry.getKey(), entry.getValue());
            }
        }

        if (cleaned.containsKey("type") && cleaned.get("type") == null) {
            cleaned.put("type", "object");
        }

        if (cleaned.get("properties") instanceof Map<?, ?> properties) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    nested.put(key, sanitizeJsonSchema(entry.getValue()));
                }
            }
            cleaned.put("properties", nested);
        }

        for (String keyword : List.of("items", "additionalProperties", "not")) {
            if (cleaned.containsKey(keyword)) {
                cleaned.put(keyword, sanitizeJsonSchema(cleaned.get(keyword)));
            }
        }

        for (String keyword : List.of("anyOf", "oneOf", "allOf")) {
            if (cleaned.get(keyword) instanceof List<?> items) {
                cleaned.put(keyword, items.stream().map(ParsingUtils::sanitizeJsonSchema).toList());
            }
        }

        return cleaned;
    }

    public static Map<String, Object> extractJsonObject(Object text) {
        if (text instanceof Map<?, ?> rawMap) {
            return castMap(rawMap);
        }
        if (text == null) {
            return Map.of();
        }

        String raw = String.valueOf(text).trim();
        if (raw.isEmpty()) {
            return Map.of();
        }

        String markerResult = "### Result";
        String markerRan = "### Ran Playwright code";
        if (raw.contains(markerResult) && raw.contains(markerRan)) {
            int start = raw.indexOf(markerResult) + markerResult.length();
            int end = raw.indexOf(markerRan, start);
            if (end > start) {
                raw = raw.substring(start, end).trim();
            }
        }

        for (int i = 0; i < 2; i++) {
            Object parsed = tryParse(raw);
            if (parsed instanceof Map<?, ?> parsedMap) {
                return castMap(parsedMap);
            }
            if (parsed instanceof String parsedString) {
                raw = parsedString.trim();
                continue;
            }
            break;
        }

        if (raw.contains("```json")) {
            int start = raw.indexOf("```json") + "```json".length();
            int end = raw.indexOf("```", start);
            if (end > start) {
                Object parsed = tryParse(raw.substring(start, end).trim());
                if (parsed instanceof Map<?, ?> parsedMap) {
                    return castMap(parsedMap);
                }
            }
        }

        int first = raw.indexOf('{');
        int last = raw.lastIndexOf('}');
        if (first >= 0 && last > first) {
            Object parsed = tryParse(raw.substring(first, last + 1));
            if (parsed instanceof Map<?, ?> parsedMap) {
                return castMap(parsedMap);
            }
        }

        return Map.of();
    }

    private static Object tryParse(String raw) {
        try {
            return OBJECT_MAPPER.readValue(raw, Object.class);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private static Map<String, Object> castMap(Map<?, ?> rawMap) {
        Map<String, Object> casted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String key) {
                casted.put(key, entry.getValue());
            }
        }
        return casted;
    }
}
