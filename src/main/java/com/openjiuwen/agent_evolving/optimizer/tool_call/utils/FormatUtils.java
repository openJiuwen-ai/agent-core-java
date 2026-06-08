/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON parsing utilities for tool optimizer.
 *
 * <p>Mirrors Python's {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/format.py}.</p>
 */
public final class FormatUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FormatUtils() {
        // Utility class
    }

    /**
     * Parse JSON-like content from model output.
     *
     * @param output raw model output
     * @param header optional header key used to locate the JSON object
     * @return parsed JSON-compatible object
     */
    public static Object parseJson(String output, String header) {
        try {
            String jsonCandidate = extractJsonCandidate(output, header);
            try {
                return OBJECT_MAPPER.readValue(jsonCandidate, Object.class);
            } catch (Exception ignored) {
                return OBJECT_MAPPER.readValue(normalizePythonLiteral(jsonCandidate), Object.class);
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse JSON from output.", exception);
        }
    }

    /**
     * Parse JSON-like content from model output.
     *
     * @param output raw model output
     * @return parsed JSON-compatible object
     */
    public static Object parseJson(String output) {
        return parseJson(output, null);
    }

    /**
     * Concatenate prompts for llama-style models.
     *
     * @param systemPrompt system prompt
     * @param userPrompt user prompt
     * @return concatenated prompt text
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

        int jsonEndIdx = text.lastIndexOf('}');
        if (jsonIdx == -1 || jsonEndIdx == -1 || jsonEndIdx < jsonIdx) {
            return text.trim();
        }
        return text.substring(jsonIdx, jsonEndIdx + 1).trim();
    }

    private static String normalizePythonLiteral(String input) {
        String text = input != null ? input.trim() : "";
        StringBuilder normalized = new StringBuilder(text.length());
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaping = false;

        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
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
                if (text.startsWith("True", index)) {
                    normalized.append("true");
                    index += 3;
                    continue;
                }
                if (text.startsWith("False", index)) {
                    normalized.append("false");
                    index += 4;
                    continue;
                }
                if (text.startsWith("None", index)) {
                    normalized.append("null");
                    index += 3;
                    continue;
                }
            }
            normalized.append(ch);
        }
        return normalized.toString();
    }
}
