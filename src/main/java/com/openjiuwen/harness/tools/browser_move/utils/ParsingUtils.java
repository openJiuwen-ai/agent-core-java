/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared parsing helpers for runtime/controller responses.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.tools.browser_move.utils.parsing}.
 */
public final class ParsingUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> UNSUPPORTED_SCHEMA_KEYS = Collections.unmodifiableSet(
            new HashSet<>(java.util.Arrays.asList("$schema", "$id", "$defs", "definitions", "$comment", "$anchor", "$vocabulary"))
    );

    private ParsingUtils() {
    }

    public static Object sanitizeJsonSchema(Object schema) {
        if (!(schema instanceof Map)) {
            return schema;
        }
        Map<String, Object> schemaMap = new LinkedHashMap<>((Map<String, Object>) schema);
        for (String kw : java.util.Arrays.asList("anyOf", "oneOf")) {
            Object variantsObj = schemaMap.get(kw);
            if (variantsObj instanceof List) {
                List<?> variants = (List<?>) variantsObj;
                if (variants.size() == 2) {
                    List<Object> nonNull = new java.util.ArrayList<>();
                    int nullCount = 0;
                    for (Object v : variants) {
                        boolean isNull = v instanceof Map && "null".equals(((Map<?, ?>) v).get("type"));
                        if (!isNull && !v.equals(Map.of("type", "null"))) {
                            nonNull.add(v);
                        } else {
                            nullCount++;
                        }
                    }
                    if (nullCount == 1 && nonNull.size() == 1) {
                        Map<String, Object> merged = new LinkedHashMap<>(schemaMap);
                        merged.remove(kw);
                        if (nonNull.get(0) instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> subMap = (Map<String, Object>) nonNull.get(0);
                            merged.putAll(subMap);
                        }
                        schemaMap = merged;
                        break;
                    }
                }
            }
        }

        Map<String, Object> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schemaMap.entrySet()) {
            if (!UNSUPPORTED_SCHEMA_KEYS.contains(entry.getKey())) {
                cleaned.put(entry.getKey(), entry.getValue());
            }
        }

        if (cleaned.containsKey("type") && cleaned.get("type") == null) {
            cleaned.put("type", "object");
        }

        if (cleaned.containsKey("properties") && cleaned.get("properties") instanceof Map) {
            Map<String, Object> props = new LinkedHashMap<>((Map<String, Object>) cleaned.get("properties"));
            Map<String, Object> sanitizedProps = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                sanitizedProps.put(entry.getKey(), sanitizeJsonSchema(entry.getValue()));
            }
            cleaned.put("properties", sanitizedProps);
        }

        for (String kw : java.util.Arrays.asList("items", "additionalProperties", "not")) {
            if (cleaned.containsKey(kw)) {
                cleaned.put(kw, sanitizeJsonSchema(cleaned.get(kw)));
            }
        }

        for (String kw : java.util.Arrays.asList("anyOf", "oneOf", "allOf")) {
            if (cleaned.containsKey(kw) && cleaned.get(kw) instanceof List) {
                List<?> variants = (List<?>) cleaned.get(kw);
                List<Object> sanitized = new java.util.ArrayList<>();
                for (Object v : variants) {
                    sanitized.add(sanitizeJsonSchema(v));
                }
                cleaned.put(kw, sanitized);
            }
        }

        return cleaned;
    }

    public static Map<String, Object> extractJsonObject(Object text) {
        if (text instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) text);
        }
        if (text == null) {
            return Collections.emptyMap();
        }

        String raw = String.valueOf(text).trim();
        if (raw.isEmpty()) {
            return Collections.emptyMap();
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
            try {
                Object parsed = OBJECT_MAPPER.readValue(raw, Object.class);
                if (parsed instanceof Map) {
                    return new LinkedHashMap<>((Map<String, Object>) parsed);
                }
                if (parsed instanceof String) {
                    raw = ((String) parsed).trim();
                    continue;
                }
                break;
            } catch (Exception ignored) {
                break;
            }
        }

        if (raw.contains("```json")) {
            int start = raw.indexOf("```json") + "```json".length();
            int end = raw.indexOf("```", start);
            if (end > start) {
                String block = raw.substring(start, end).trim();
                try {
                    Object parsed = OBJECT_MAPPER.readValue(block, Object.class);
                    if (parsed instanceof Map) {
                        return new LinkedHashMap<>((Map<String, Object>) parsed);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        int first = raw.indexOf('{');
        int last = raw.lastIndexOf('}');
        if (first >= 0 && last > first) {
            String snippet = raw.substring(first, last + 1);
            try {
                Object parsed = OBJECT_MAPPER.readValue(snippet, Object.class);
                if (parsed instanceof Map) {
                    return new LinkedHashMap<>((Map<String, Object>) parsed);
                }
            } catch (Exception ignored) {
            }
        }

        return Collections.emptyMap();
    }
}