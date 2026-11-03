/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.List;
import java.util.Map;

/**
 * Formats prompts for LLM components, injecting format instructions
 * (markdown / JSON schema) into the last user message.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.llm_comp.LLMPromptFormatter}.
  * Python file: {@code openjiuwen/core/workflow/components/llm/llm_comp.py}.
 */
public final class LLMPromptFormatter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DEFAULT_MARKDOWN_INSTRUCTION =
            "Please return the answer in markdown format.\n"
            + "- For headings, use number signs (#).\n"
            + "- For list items, start with dashes (-).\n"
            + "- To emphasize text, wrap it with asterisks (*).\n"
            + "- For code or commands, surround them with backticks (`).\n"
            + "- For quoted text, use greater than signs (>).\n"
            + "- For links, wrap the text in square brackets [], followed by the URL in parentheses ().\n"
            + "- For images, use square brackets [] for the alt text, followed by the image URL in parentheses ().\n"
            + "The question is: ${query}.";

    private static final String DEFAULT_JSON_INSTRUCTION =
            "Carefully consider the user's question to ensure your answer is logical and makes sense.\n"
            + "- Make sure your explanation is concise and easy to understand, not verbose.\n"
            + "- Strictly return the answer in valid JSON format only, and "
            + "\"DO NOT ADD ANY COMMENTS BEFORE OR AFTER IT\" to ensure it could be formatted "
            + "as a JSON instance that conforms to the JSON schema below.\n"
            + "Here is the JSON schema: ${json_schema}.\n"
            + "The question is: ${query}.";

    private LLMPromptFormatter() {
    }

    /**
     * Format prompt history with response format instructions.
     *
     * @param history        list of messages
     * @param responseFormat response format config
     * @param outputConfig   output config
     * @return formatted message list
     */
    public static List<BaseMessage> formatPrompt(List<BaseMessage> history,
                                                  Map<String, Object> responseFormat,
                                                  Map<String, Object> outputConfig) {
        String resType = (String) responseFormat.get("type");
        if ("text".equals(resType)) {
            return history;
        }

        Integer lastUserIdx = findLastUserIndex(history);
        if (lastUserIdx == null) {
            return history;
        }

        String query = getContentAsString(history.get(lastUserIdx));
        String prompt;

        if ("markdown".equals(resType)) {
            String instruction = responseFormat.containsKey("markdownInstruction")
                    ? (String) responseFormat.get("markdownInstruction")
                    : DEFAULT_MARKDOWN_INSTRUCTION;
            prompt = instruction.replace("${query}", query);
        } else if ("json".equals(resType)) {
            Object configType = outputConfig.get("type");
            Map<String, Object> jsonSchema;
            if (configType instanceof String && "object".equals(configType)) {
                jsonSchema = outputConfig;
            } else {
                jsonSchema = SchemaGenerator.generateJsonSchema(outputConfig);
            }
            String instruction = responseFormat.containsKey("jsonInstruction")
                    ? (String) responseFormat.get("jsonInstruction")
                    : DEFAULT_JSON_INSTRUCTION;
            try {
                String schemaStr = MAPPER.writeValueAsString(jsonSchema);
                prompt = instruction.replace("${json_schema}", schemaStr).replace("${query}", query);
            } catch (Exception e) {
                prompt = instruction.replace("${query}", query);
            }
        } else {
            return history;
        }

        history.get(lastUserIdx).setContent(prompt);
        return history;
    }

    private static Integer findLastUserIndex(List<BaseMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).getRole())) {
                return i;
            }
        }
        return null;
    }

    private static String getContentAsString(BaseMessage message) {
        Object content = message.getContent();
        if (content instanceof String s) {
            return s;
        }
        return content != null ? content.toString() : "";
    }
}
