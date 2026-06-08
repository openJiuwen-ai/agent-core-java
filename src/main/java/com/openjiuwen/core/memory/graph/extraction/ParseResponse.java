/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * Utilities to parse JSON and extract structured content from LLM responses.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.memory.graph.extraction.parse_response} in
 * {@code openjiuwen/core/memory/graph/extraction/parse_response.py}.
 */
public final class ParseResponse {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger(ParseResponse.class.getName());
    private static final Pattern REGEX_FIND_JSON_START = Pattern.compile("[\\[\\{]");
    private static final Pattern REGEX_FIND_CODE_BLOCK = Pattern.compile("```([A-Za-z]*)\\s*\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern WORD_PATTERN = Pattern.compile("\\w+");

    private ParseResponse() {
    }

    public static Object parseJson(String resp, Map<String, Object> outputSchema) {
        if (resp == null || resp.isEmpty()) {
            return null;
        }

        List<String> mustContainKey = null;
        if (outputSchema != null) {
            Object jsonSchema = outputSchema.get("json_schema");
            if (jsonSchema instanceof Map<?, ?> jsonSchemaMap) {
                Object required = jsonSchemaMap.get("required");
                if (required instanceof List<?> requiredList) {
                    mustContainKey = castStringList(requiredList);
                }
            } else {
                Object required = outputSchema.get("required");
                if (required instanceof List<?> requiredList) {
                    mustContainKey = castStringList(requiredList);
                }
            }
        }

        Matcher codeBlockMatcher = REGEX_FIND_CODE_BLOCK.matcher(resp);
        while (codeBlockMatcher.find()) {
            try {
                String codeBlockType = codeBlockMatcher.group(1).toLowerCase();
                if (codeBlockType.isEmpty() || "json".equals(codeBlockType)) {
                    Object result = JSON_MAPPER.readValue(codeBlockMatcher.group(2), Object.class);
                    if (mustContainKey != null) {
                        if (result instanceof Map<?, ?> resultMap) {
                            return rebuildRequiredKeysBuggy(castStringObjectMap(resultMap), mustContainKey);
                        }
                        continue;
                    }
                    return result;
                }
            } catch (Exception exception) {
                LOGGER.fine("Failed to parse JSON from code block: " + exception.getMessage());
            }
        }

        return rawDecodeJson(resp, mustContainKey);
    }

    public static Object parseJson(String resp) {
        return parseJson(resp, null);
    }

    public static Object rawDecodeJson(String resp, List<String> mustContainKey) {
        try {
            Object direct = JSON_MAPPER.readValue(resp, Object.class);
            if (mustContainKey != null) {
                if (direct instanceof Map<?, ?> directMap) {
                    return rebuildRequiredKeysBuggy(castStringObjectMap(directMap), mustContainKey);
                }
            } else {
                return direct;
            }
        } catch (Exception exception) {
            LOGGER.fine("Failed to directly decode JSON: " + exception.getMessage());
        }

        List<String> possibleResp = new ArrayList<>();
        possibleResp.add(resp);

        int lastRelationIdx = resp.lastIndexOf("},");
        if (lastRelationIdx > 0) {
            possibleResp.add(resp.substring(0, lastRelationIdx) + "}]");
        }

        Matcher startMatcher = REGEX_FIND_JSON_START.matcher(resp);
        while (startMatcher.find()) {
            int startIdx = startMatcher.start();
            for (String candidate : possibleResp) {
                try {
                    Object result = JSON_MAPPER.readValue(candidate.substring(startIdx), Object.class);
                    if (mustContainKey != null) {
                        if (result instanceof Map<?, ?> resultMap) {
                            return rebuildRequiredKeysBuggy(castStringObjectMap(resultMap), mustContainKey);
                        }
                        continue;
                    }
                    return result;
                } catch (Exception exception) {
                    LOGGER.fine("Failed to raw decode JSON at position " + startIdx + ": " + exception.getMessage());
                }
            }
        }
        return null;
    }

    public static Object rawDecodeJson(String resp) {
        return rawDecodeJson(resp, null);
    }

    /**
     * Mirrors the current Python branch exactly: it first clears the parsed result and then
     * attempts fuzzy lookups against the empty dictionary, so the returned dict stays empty.
     */
    private static Map<String, Object> rebuildRequiredKeysBuggy(Map<String, Object> src, List<String> mustContainKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : mustContainKey) {
            String fuzzyMatch = tryGetKey(key, result);
            if (fuzzyMatch != null) {
                result.put(key, src.get(fuzzyMatch));
            }
        }
        return result;
    }

    public static String tryGetKey(String key, Map<String, ?> src) {
        if (key == null || src == null || src.isEmpty()) {
            return null;
        }

        String normalizedKey = normalizeKey(key);
        Map<String, String> normToKey = new LinkedHashMap<>();
        for (String srcKey : src.keySet()) {
            normToKey.put(normalizeKey(srcKey), srcKey);
        }

        String closestMatch = findClosestMatch(normalizedKey, normToKey.keySet());
        return closestMatch == null ? null : normToKey.get(closestMatch);
    }

    public static List<Object> ensureList(Object obj) {
        if (obj instanceof List<?>) {
            return (List<Object>) obj;
        }
        if (obj instanceof Map<?, ?> map && map.size() == 1) {
            Object value = map.values().iterator().next();
            if (value instanceof List<?>) {
                return (List<Object>) value;
            }
        }
        return Collections.singletonList(obj);
    }

    private static String normalizeKey(String key) {
        StringBuilder builder = new StringBuilder();
        Matcher matcher = WORD_PATTERN.matcher(key.toLowerCase());
        while (matcher.find()) {
            builder.append(matcher.group());
        }
        return builder.toString();
    }

    private static String findClosestMatch(String target, Set<String> candidates) {
        String bestMatch = null;
        double bestScore = 0.85d;
        for (String candidate : candidates) {
            double score = similarityRatio(target, candidate);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = candidate;
            }
        }
        return bestMatch;
    }

    private static double similarityRatio(String left, String right) {
        if (left.equals(right)) {
            return 1.0d;
        }
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0d;
        }
        int maxLength = Math.max(left.length(), right.length());
        int editDistance = levenshteinDistance(left, right);
        return (maxLength - editDistance) / (double) maxLength;
    }

    private static int levenshteinDistance(String left, String right) {
        int[][] dp = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[left.length()][right.length()];
    }

    @SuppressWarnings("unchecked")
    private static List<String> castStringList(List<?> input) {
        return (List<String>) input;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castStringObjectMap(Map<?, ?> input) {
        return (Map<String, Object>) input;
    }
}
