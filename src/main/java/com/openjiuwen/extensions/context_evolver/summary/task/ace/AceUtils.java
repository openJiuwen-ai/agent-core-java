/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility functions for ACE operations.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.ace.utils}.
 */
public final class AceUtils {

    private static final Logger logger = LoggerFactory.getLogger(AceUtils.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern JSON_CODE_BLOCK = Pattern.compile("```(?:json)?\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    private AceUtils() {
    }

    /**
     * Safely load JSON from string with error handling.
     *
     * @param text String containing JSON data
     * @return Parsed JSON as Map
     * @throws IllegalArgumentException If JSON parsing fails
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> safeJsonLoads(String text) {
        try {
            return objectMapper.readValue(text, Map.class);
        } catch (Exception directError) {
            Matcher codeBlockMatcher = JSON_CODE_BLOCK.matcher(text);
            if (codeBlockMatcher.find()) {
                try {
                    return objectMapper.readValue(codeBlockMatcher.group(1), Map.class);
                } catch (Exception ignored) {
                    // Fall through to the broad JSON extraction path.
                }
            }

            Matcher jsonMatcher = JSON_OBJECT.matcher(text);
            if (jsonMatcher.find()) {
                try {
                    return objectMapper.readValue(jsonMatcher.group(0), Map.class);
                } catch (Exception ignored) {
                    // Fall through to the final error.
                }
            }

            String preview = text != null && text.length() > 200 ? text.substring(0, 200) : String.valueOf(text);
            logger.error("Failed to parse JSON from text: {}...", preview);
            throw new IllegalArgumentException("Could not parse valid JSON from response");
        }
    }

    public static String normalizeForMatch(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", " ")
            .trim()
            .replaceAll("\\s+", " ");
    }

    public static List<String> extractPrefixedLines(String text, String prefix) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank() || prefix == null || prefix.isBlank()) {
            return lines;
        }
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith(prefix)) {
                lines.add(line.substring(prefix.length()).trim());
            }
        }
        return lines;
    }

    public static List<String> extractObservationKeys(String text) {
        Set<String> keys = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        Matcher matcher = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:").matcher(text);
        while (matcher.find()) {
            keys.add(matcher.group(1));
            if (keys.size() >= 6) {
                break;
            }
        }
        return new ArrayList<>(keys);
    }

    public static String guessSection(String query, String trajectory) {
        String combined = normalizeForMatch(query + " " + trajectory);
        if (combined.contains("api") || combined.contains("action") || combined.contains("spotify")
            || combined.contains("search ") || combined.contains("query ")) {
            return "apis_to_use_for_specific_information";
        }
        if (combined.contains("format") || combined.contains("schema") || combined.contains("json")) {
            return "output_format_and_validation";
        }
        return "strategies_and_hard_rules";
    }

    public static int trailingCounter(String bulletId) {
        if (bulletId == null || bulletId.isBlank()) {
            return 0;
        }
        int separator = bulletId.lastIndexOf('-');
        if (separator < 0 || separator == bulletId.length() - 1) {
            return 0;
        }
        try {
            return Integer.parseInt(bulletId.substring(separator + 1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static String compactWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }
}
