/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities to parse JSON and structured content from LLM responses.
 * 
 * @since 0.1.7
 */
public final class ParseResponse {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern REGEX_FIND_JSON_START = Pattern.compile("[\\[{]");

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern REGEX_FIND_CODE_BLOCK = Pattern.compile("(?s)```([A-Za-z]*)\\s*\\n(.*?)```");

    /**
     * ParseResponse.
     * 
     * @since 0.1.7
     */
    private ParseResponse() {
    }

    /**
     * parseJson.
     * 
     * @param response response
     * @param outputSchema outputSchema
     * @return the result
     * @since 0.1.7
     */
    public static Object parseJson(String response, Map<String, Object> outputSchema) {
        List<String> mustContainKeys = null;
        if (outputSchema != null) {
            Object schema = outputSchema.getOrDefault("json_schema", outputSchema);
            if (schema instanceof Map<?, ?> schemaMap) {
                Object required = schemaMap.get("required");
                if (required instanceof List<?> list) {
                    mustContainKeys = list.stream().map(String::valueOf).toList();
                } else if (schemaMap.get("schema") instanceof Map<?, ?> nested) {
                    Object nestedRequired = nested.get("required");
                    if (nestedRequired instanceof List<?> list) {
                        mustContainKeys = list.stream().map(String::valueOf).toList();
                    }
                } else {
                    // no-op
                }
            }
        }
        Matcher codeBlockMatcher = REGEX_FIND_CODE_BLOCK.matcher(response);
        while (codeBlockMatcher.find()) {
            String codeBlockType = codeBlockMatcher.group(1).toLowerCase(Locale.ROOT);
            if (!codeBlockType.isBlank() && !"json".equals(codeBlockType)) {
                continue;
            }
            Object parsed = tryParse(codeBlockMatcher.group(2));
            Object normalized = normalizeRequiredKeys(parsed, mustContainKeys);
            if (normalized != null) {
                return normalized;
            }
        }
        return rawDecodeJson(response, mustContainKeys);
    }

    /**
     * rawDecodeJson.
     * 
     * @param response response
     * @param mustContainKeys mustContainKeys
     * @return the result
     * @since 0.1.7
     */
    public static Object rawDecodeJson(String response, List<String> mustContainKeys) {
        List<String> candidates = new ArrayList<>();
        candidates.add(response);
        int lastRelationIdx = response.lastIndexOf("},");
        if (lastRelationIdx > 0) {
            candidates.add(response.substring(0, lastRelationIdx) + "}]");
        }
        Matcher startMatcher = REGEX_FIND_JSON_START.matcher(response);
        while (startMatcher.find()) {
            int start = startMatcher.start();
            for (String candidate : candidates) {
                Object parsed = tryParse(candidate.substring(start));
                Object normalized = normalizeRequiredKeys(parsed, mustContainKeys);
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        return null;
    }

    /**
     * tryGetKey.
     * 
     * @param key key
     * @param source source
     * @return the result
     * @since 0.1.7
     */
    public static Object tryGetKey(String key, Map<String, Object> source) {
        String normalizedKey = normalizeToken(key);
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (normalizeToken(entry.getKey()).equals(normalizedKey)) {
                return entry.getValue();
            }
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (normalizeToken(entry.getKey()).contains(normalizedKey)
                    || normalizedKey.contains(normalizeToken(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * ensureList.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static List<Object> ensureList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof Map<?, ?> map && map.size() == 1) {
            Object inner = map.values().iterator().next();
            if (inner instanceof List<?> list) {
                return new ArrayList<>(list);
            }
        }
        return List.of(value);
    }

    /**
     * tryParse.
     * 
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    private static Object tryParse(String content) {
        try {
            return MAPPER.readValue(content, new TypeReference<Object>() {
            });
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * normalizeRequiredKeys.
     * 
     * @param parsed parsed
     * @param mustContainKeys mustContainKeys
     * @return the result
     * @since 0.1.7
     */
    private static Object normalizeRequiredKeys(Object parsed, List<String> mustContainKeys) {
        if (parsed == null) {
            return null;
        }
        if (mustContainKeys == null || mustContainKeys.isEmpty()) {
            return parsed;
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (String key : mustContainKeys) {
            Object value = tryGetKey(key, (Map<String, Object>) map);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * normalizeToken.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String normalizeToken(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }
}
