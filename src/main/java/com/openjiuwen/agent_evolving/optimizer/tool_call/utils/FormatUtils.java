// coding: utf-8
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

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
            try {
                String jsonStr = extractJsonCandidate(output, header);
                return OBJECT_MAPPER.readValue(jsonStr, Object.class);
            } catch (Exception ignored) {
                String literal = normalizePythonLiteral(extractJsonCandidate(output, header));
                return OBJECT_MAPPER.readValue(literal, Object.class);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse JSON from output.", e);
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

    private static String extractJsonCandidate(String output, String header) {
        String text = output != null ? output : "";
        int jsonIdx = -1;
        if (header != null) {
            jsonIdx = text.indexOf("{\"" + header + "\":");
            if (jsonIdx == -1) {
                jsonIdx = text.indexOf("{\n\"" + header + "\":");
            }
        }
        if (jsonIdx == -1) {
            jsonIdx = text.indexOf("{\n");
        }
        if (jsonIdx == -1) {
            jsonIdx = text.indexOf('{');
        }
        if (jsonIdx == -1) {
            jsonIdx = text.indexOf("[\n");
        }
        if (jsonIdx == -1) {
            jsonIdx = text.indexOf('[');
        }
        if (jsonIdx == -1) {
            return text.trim();
        }

        char closing = text.charAt(jsonIdx) == '[' ? ']' : '}';
        int jsonEndIdx = text.lastIndexOf(closing);
        if (jsonEndIdx == -1 || jsonEndIdx < jsonIdx) {
            jsonEndIdx = text.length() - 1;
        }
        return text.substring(jsonIdx, jsonEndIdx + 1).trim();
    }

    private static String normalizePythonLiteral(String input) {
        String text = input != null ? input.trim() : "";
        StringBuilder normalized = new StringBuilder(text.length());
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaping = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaping) {
                normalized.append(ch);
                escaping = false;
                continue;
            }
            if ((inSingle || inDouble) && ch == '\\') {
                normalized.append(ch);
                escaping = true;
                continue;
            }
            if (!inDouble && ch == '\'') {
                normalized.append('"');
                inSingle = !inSingle;
                continue;
            }
            if (!inSingle && ch == '"') {
                normalized.append(ch);
                inDouble = !inDouble;
                continue;
            }
            if (!inSingle && !inDouble) {
                if (text.startsWith("True", i)) {
                    normalized.append("true");
                    i += 3;
                    continue;
                }
                if (text.startsWith("False", i)) {
                    normalized.append("false");
                    i += 4;
                    continue;
                }
                if (text.startsWith("None", i)) {
                    normalized.append("null");
                    i += 3;
                    continue;
                }
            }
            normalized.append(ch);
        }
        return normalized.toString();
    }
}
