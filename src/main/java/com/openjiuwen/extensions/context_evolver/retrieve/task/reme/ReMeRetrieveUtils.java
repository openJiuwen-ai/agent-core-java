/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reme;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility functions for ReMe retrieval operations.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.context_evolver.retrieve.task.reme.utils}.
 */
public class ReMeRetrieveUtils {

    private static final Logger log = LoggerFactory.getLogger(ReMeRetrieveUtils.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Pattern to extract JSON blocks from markdown code fences.
     */
    private static final Pattern JSON_CODE_FENCE_PATTERN = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");

    /**
     * Pattern to extract numbers from text.
     */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+\\b");

    /**
     * Parse LLM response to extract a list of integers from JSON.
     *
     * @param response the LLM response string that may contain JSON
     * @param key      the key to extract from the JSON object
     * @return list of integers from the specified key, or empty list if parsing fails
     */
    public static List<Integer> parseJsonListResponse(String response, String key) {
        try {
            // Try to extract JSON blocks from markdown code fences
            Matcher matcher = JSON_CODE_FENCE_PATTERN.matcher(response);
            if (matcher.find()) {
                String jsonContent = matcher.group(1).trim();
                JsonNode parsed = objectMapper.readTree(jsonContent);

                if (parsed.isObject() && parsed.has(key)) {
                    JsonNode valueNode = parsed.get(key);
                    if (valueNode.isArray()) {
                        List<Integer> result = new ArrayList<>();
                        for (JsonNode node : valueNode) {
                            if (node.isInt()) {
                                result.add(node.asInt());
                            }
                        }
                        return result;
                    }
                } else if (parsed.isArray()) {
                    List<Integer> result = new ArrayList<>();
                    for (JsonNode node : parsed) {
                        if (node.isInt()) {
                            result.add(node.asInt());
                        }
                    }
                    return result;
                }
            }

            // Fallback: try to extract numbers from text
            List<Integer> numbers = new ArrayList<>();
            Matcher numMatcher = NUMBER_PATTERN.matcher(response);
            while (numMatcher.find()) {
                int num = Integer.parseInt(numMatcher.group());
                if (num < 100) {  // Reasonable upper bound
                    numbers.add(num);
                }
            }
            return numbers;

        } catch (Exception e) {
            log.error("Error parsing list response for key '{}': {}", key, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Parse LLM response to extract a list of integers from JSON using default key "ranked_indices".
     *
     * @param response the LLM response string that may contain JSON
     * @return list of integers from the default key, or empty list if parsing fails
     */
    public static List<Integer> parseJsonListResponse(String response) {
        return parseJsonListResponse(response, "ranked_indices");
    }

    /**
     * Parse JSON response to extract a specific string field.
     *
     * @param response the response string that may contain JSON
     * @param key      the key to extract from the JSON object
     * @return the value associated with the key, or null if parsing fails
     */
    public static Optional<String> parseJsonField(String response, String key) {
        try {
            // Try to extract JSON blocks from markdown code fences
            Matcher matcher = JSON_CODE_FENCE_PATTERN.matcher(response);
            if (matcher.find()) {
                String jsonContent = matcher.group(1).trim();
                JsonNode parsed = objectMapper.readTree(jsonContent);

                if (parsed.isObject() && parsed.has(key)) {
                    JsonNode valueNode = parsed.get(key);
                    if (valueNode.isTextual()) {
                        return Optional.of(valueNode.asText());
                    }
                }
            }

            // Fallback: try to parse the entire response as JSON
            JsonNode parsed = objectMapper.readTree(response);
            if (parsed.isObject() && parsed.has(key)) {
                JsonNode valueNode = parsed.get(key);
                if (valueNode.isTextual()) {
                    return Optional.of(valueNode.asText());
                }
            }

        } catch (Exception e) {
            log.warn("Failed to parse JSON response for key '{}'", key);
        }

        return Optional.empty();
    }

    /**
     * Parse JSON response to extract a specific string field, returning null instead of Optional.
     *
     * @param response the response string that may contain JSON
     * @param key      the key to extract from the JSON object
     * @return the value associated with the key, or null if parsing fails
     */
    public static String parseJsonFieldOrNull(String response, String key) {
        return parseJsonField(response, key).orElse(null);
    }
}