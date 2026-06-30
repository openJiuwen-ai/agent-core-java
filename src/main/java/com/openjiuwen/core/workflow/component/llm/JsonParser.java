/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * JSON parser for LLM response content.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.llm_comp.JsonParser}.
 */
public final class JsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonParser() {
    }

    /**
     * Parse JSON content from LLM response, stripping markdown code blocks if present.
     *
     * @param responseContent the raw response content
     * @return parsed map
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public static Map<String, Object> parseJsonContent(String responseContent) {
        String content = cleanMarkdownBlocks(responseContent);

        try {
            return MAPPER.readValue(content, Map.class);
        } catch (JsonProcessingException e) {
            ValidationUtils.raiseInvalidParamsError("Json parse error: " + responseContent);
            return Map.of(); // unreachable
        }
    }

    /**
     * Remove markdown code block wrappers from content.
     */
    static String cleanMarkdownBlocks(String content) {
        if (content == null) {
            return "";
        }
        content = content.strip();
        if (content.startsWith("```") && content.endsWith("```")) {
            String[] lines = content.split("\n");
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < lines.length; i++) {
                if (i == lines.length - 1 && "```".equals(lines[i].trim())) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(lines[i]);
            }
            return sb.toString().strip();
        }
        return content;
    }
}
