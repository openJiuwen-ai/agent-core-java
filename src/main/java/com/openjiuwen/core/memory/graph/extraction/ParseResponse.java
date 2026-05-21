/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities to parse JSON and extract structured content from LLM responses.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.memory.graph.extraction.parse_response}.
 */
public final class ParseResponse {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** Pattern to find JSON start characters ([ or {) */
    private static final Pattern REGEX_FIND_JSON_START = Pattern.compile("[\\[\\{]");

    /** Pattern to find code blocks in markdown format */
    private static final Pattern REGEX_FIND_CODE_BLOCK = Pattern.compile("```([A-Za-z]*)\\s*\\n(.*?)```", Pattern.DOTALL);

    /** Pattern to extract word characters from keys */
    private static final Pattern WORD_PATTERN = Pattern.compile("\\w+");

    private ParseResponse() {
    }

    /**
     * Attempt to parse JSON from LLM response.
     *
     * @param resp          the raw LLM response string
     * @param outputSchema  optional schema with required keys for fuzzy matching
     * @return parsed JSON object (Map, List, or primitive), or null if parsing fails
     */
    public static Object parseJson(String resp, Map<String, Object> outputSchema) {
        if (resp == null || resp.isEmpty()) {
            return null;
        }

        List<String> mustContainKey = null;
        if (outputSchema != null) {
            Object jsonSchema = outputSchema.get("json_schema");
            if (jsonSchema instanceof Map) {
                Object required = ((Map<?, ?>) jsonSchema).get("required");
                if (required instanceof List) {
                    mustContainKey = (List<String>) required;
                }
            } else {
                Object required = outputSchema.get("required");
                if (required instanceof List) {
                    mustContainKey = (List<String>) required;
                }
            }
        }

        // Try to find JSON in code blocks first
        Matcher codeBlockMatcher = REGEX_FIND_CODE_BLOCK.matcher(resp);
        while (codeBlockMatcher.find()) {
            try {
                String codeBlockType = codeBlockMatcher.group(1).toLowerCase();
                if (codeBlockType.isEmpty() || "json".equals(codeBlockType)) {
                    String content = codeBlockMatcher.group(2);
                    Object result = JSON_MAPPER.readValue(content, Object.class);
                    if (mustContainKey != null && result instanceof Map) {
                        return extractRequiredKeys((Map<String, Object>) result, mustContainKey);
                    }
                    return result;
                }
            } catch (Exception e) {
                Loggers.MEMORY.debug("Failed to parse JSON from code block: {}", e.getMessage());
            }
        }

        // Fallback to raw decode
        return rawDecodeJson(resp, mustContainKey);
    }

    /**
     * Attempt to parse JSON without code block markers.
     */
    private static Object rawDecodeJson(String resp, List<String> mustContainKey) {
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
                    if (mustContainKey != null && result instanceof Map) {
                        return extractRequiredKeys((Map<String, Object>) result, mustContainKey);
                    }
                    return result;
                } catch (Exception e) {
                    Loggers.MEMORY.debug("Failed to raw decode JSON at position {}: {}", startIdx, e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * Extract required keys from a parsed result using fuzzy matching.
     */
    private static Map<String, Object> extractRequiredKeys(Map<String, Object> src, List<String> mustContainKey) {
        Map<String, Object> result = new HashMap<>();
        for (String key : mustContainKey) {
            Object fuzzyMatch = tryGetKey(key, src);
            if (fuzzyMatch != null) {
                result.put(key, fuzzyMatch);
            }
        }
        return result;
    }

    /**
     * Try to get a specific key from the source dictionary using fuzzy matching.
     * <p>
     * Normalizes both the target key and source keys by extracting word characters,
     * then uses similarity matching to find the closest key.
     *
     * @param key  the target key to find
     * @param src  the source dictionary
     * @return the value associated with the closest matching key, or null if no match
     */
    public static Object tryGetKey(String key, Map<String, Object> src) {
        if (key == null || src == null || src.isEmpty()) {
            return null;
        }

        // Normalize target key
        String normalizedKey = normalizeKey(key);

        // Build normalized-to-original key mapping
        Map<String, String> norm2Key = new HashMap<>();
        for (String k : src.keySet()) {
            norm2Key.put(normalizeKey(k), k);
        }

        // Find closest match
        String closestMatch = findClosestMatch(normalizedKey, norm2Key.keySet());
        if (closestMatch != null) {
            return src.get(norm2Key.get(closestMatch));
        }
        return null;
    }

    /**
     * Normalize a key by extracting word characters and converting to lowercase.
     */
    private static String normalizeKey(String key) {
        StringBuilder sb = new StringBuilder();
        Matcher m = WORD_PATTERN.matcher(key.toLowerCase());
        while (m.find()) {
            sb.append(m.group());
        }
        return sb.toString();
    }

    /**
     * Find the closest matching string from a set of candidates.
     * Uses similarity ratio with 0.85 cutoff threshold.
     */
    private static String findClosestMatch(String target, Set<String> candidates) {
        String bestMatch = null;
        double bestScore = 0.85; // cutoff threshold

        for (String candidate : candidates) {
            double score = similarityRatio(target, candidate);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = candidate;
            }
        }
        return bestMatch;
    }

    /**
     * Calculate similarity ratio between two strings.
     * Simplified implementation of Python's difflib.get_close_matches logic.
     */
    private static double similarityRatio(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }

        // Use Levenshtein-like similarity
        int maxLen = Math.max(a.length(), b.length());
        int editDistance = levenshteinDistance(a, b);
        return (maxLen - editDistance) / (double) maxLen;
    }

    /**
     * Calculate Levenshtein edit distance between two strings.
     */
    private static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    /**
     * Ensure returned object is a list.
     * <p>
     * If the object is already a list, returns it directly.
     * If the object is a single-key dict whose value is a list, returns that list.
     * Otherwise, wraps the object in a single-element list.
     *
     * @param obj the object to ensure is a list
     * @return a list representation of the object
     */
    public static List<Object> ensureList(Object obj) {
        if (obj instanceof List) {
            return (List<Object>) obj;
        }
        if (obj instanceof Map && ((Map<?, ?>) obj).size() == 1) {
            Object value = ((Map<?, ?>) obj).values().iterator().next();
            if (value instanceof List) {
                return (List<Object>) value;
            }
        }
        return Collections.singletonList(obj);
    }
}
