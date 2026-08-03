/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code _safe_json_loads} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/ace/utils.py}.
 */
public final class AceUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern JSON_CODE_BLOCK = Pattern.compile("```(?:json)?\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private AceUtils() {
    }

    public static Map<String, Object> safeJsonLoads(String text) {
        try {
            return OBJECT_MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {
            });
        } catch (com.fasterxml.jackson.core.JsonProcessingException directError) {
            Matcher codeBlockMatcher = JSON_CODE_BLOCK.matcher(text);
            if (codeBlockMatcher.find()) {
                try {
                    return OBJECT_MAPPER.readValue(codeBlockMatcher.group(1), new TypeReference<Map<String, Object>>() {
                    });
                } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                    // Fall through to the broad JSON extraction path.
                }
            }

            Matcher jsonMatcher = JSON_OBJECT.matcher(text);
            if (jsonMatcher.find()) {
                try {
                    return OBJECT_MAPPER.readValue(jsonMatcher.group(0), new TypeReference<Map<String, Object>>() {
                    });
                } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                    // Fall through to the final error path.
                }
            }

            String preview = text != null && text.length() > 200 ? text.substring(0, 200) : String.valueOf(text);
            LOGGER.error("Failed to parse JSON from text: {}...", preview);
            throw new IllegalArgumentException("Could not parse valid JSON from response");
        }
    }
}
