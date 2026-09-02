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
 * 
 * @since 0.1.7
 */
public final class JsonParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * JsonParser.
     * 
     * @since 0.1.7
     */
    private JsonParser() {
    }

    /**
     * parseJsonContent.
     * 
     * @param responseContent responseContent
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
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
     *
     * @param content content
     * @return String
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
