/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reme;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code parse_json_list_response} and {@code parse_json_field} in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reme/utils.py}.
 */
public final class ReMeRetrieveUtils {

    private static final Pattern JSON_CODE_FENCE_PATTERN = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+\\b");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private ReMeRetrieveUtils() {
    }

    public static List<Integer> parseJsonListResponse(String response) {
        return parseJsonListResponse(response, "ranked_indices");
    }

    public static List<Integer> parseJsonListResponse(String response, String key) {
        try {
            Matcher matcher = JSON_CODE_FENCE_PATTERN.matcher(response);
            if (matcher.find()) {
                JsonNode parsed = OBJECT_MAPPER.readTree(matcher.group(1));
                if (parsed.isObject() && parsed.has(key)) {
                    return asIntegerList(parsed.get(key));
                }
                if (parsed.isArray()) {
                    return asIntegerList(parsed);
                }
            }

            List<Integer> numbers = new ArrayList<>();
            Matcher numberMatcher = NUMBER_PATTERN.matcher(response);
            while (numberMatcher.find()) {
                int value = Integer.parseInt(numberMatcher.group());
                if (value < 100) {
                    numbers.add(value);
                }
            }
            return numbers;
        } catch (Exception exception) {
            LOGGER.error("Error parsing list response for key '{}': {}", key, exception.getMessage());
            return List.of();
        }
    }

    public static Optional<String> parseJsonField(String response, String key) {
        try {
            Matcher matcher = JSON_CODE_FENCE_PATTERN.matcher(response);
            if (matcher.find()) {
                JsonNode parsed = OBJECT_MAPPER.readTree(matcher.group(1));
                if (parsed.isObject() && parsed.has(key) && parsed.get(key).isTextual()) {
                    return Optional.of(parsed.get(key).asText());
                }
            }

            JsonNode parsed = OBJECT_MAPPER.readTree(response);
            if (parsed.isObject() && parsed.has(key) && parsed.get(key).isTextual()) {
                return Optional.of(parsed.get(key).asText());
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            LOGGER.warning("Failed to parse JSON response for key '{}'", key);
            return Optional.empty();
        }
        return Optional.empty();
    }

    public static String parseJsonFieldOrNull(String response, String key) {
        return parseJsonField(response, key).orElse(null);
    }

    private static List<Integer> asIntegerList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isInt()) {
                result.add(item.intValue());
            }
        }
        return result;
    }
}
