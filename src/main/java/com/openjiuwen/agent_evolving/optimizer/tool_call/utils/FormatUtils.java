// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.Map;

/**
 * JSON parsing utilities for tool optimizer.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.format}.
 */
public final class FormatUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FormatUtils() {
        // Utility class
    }

    /**
     * Parse JSON from LLM output string.
     *
     * @param output LLM output string
     * @param header Optional header to search for
     * @return Parsed JSON object
     */
    public static Object parseJson(String output, String header) {
        try {
            int jsonIdx = -1;
            if (header != null) {
                jsonIdx = output.indexOf("{\""+ header + "\":");
                if (jsonIdx == -1) {
                    jsonIdx = output.indexOf("{\n\"" + header + "\":");
                }
            }
            if (jsonIdx == -1) {
                jsonIdx = output.indexOf("{\n");
            }
            if (jsonIdx == -1) {
                jsonIdx = output.indexOf("{");
            }
            int jsonEndIdx = output.lastIndexOf("}");
            jsonEndIdx = jsonEndIdx != -1 ? jsonEndIdx + 1 : -1;

            String jsonStr = output.substring(jsonIdx, jsonEndIdx).trim();
            return OBJECT_MAPPER.readValue(jsonStr, Object.class);
        } catch (Exception e) {
            Loggers.AGENT.warn("Failed to parse JSON from output: {}", e.getMessage());
            // Try literal evaluation as fallback
            try {
                // Simple fallback - try to evaluate as map
                return OBJECT_MAPPER.readValue(output, Object.class);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /**
     * Parse JSON from LLM output string.
     *
     * @param output LLM output string
     * @return Parsed JSON object
     */
    public static Object parseJson(String output) {
        return parseJson(output, null);
    }

    /**
     * Format prompt for LLaMA-style models.
     *
     * @param systemPrompt System prompt
     * @param userPrompt   User prompt
     * @return Combined prompt string
     */
    public static String formatPromptLlama(String systemPrompt, String userPrompt) {
        return (systemPrompt != null ? systemPrompt : "") + (userPrompt != null ? userPrompt : "");
    }
}