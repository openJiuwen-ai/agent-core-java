/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool description reviewer for cleaning and processing.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.customized_reviewer.ToolDescriptionReviewer}.
 */
public class ToolDescriptionReviewer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String evalModelId;
    private final String llmApiKey;

    /**
     * Create tool description reviewer.
     *
     * @param evalModelId Evaluation model ID
     * @param llmApiKey   LLM API key
     */
    public ToolDescriptionReviewer(String evalModelId, String llmApiKey) {
        this.evalModelId = evalModelId;
        this.llmApiKey = llmApiKey;
    }

    /**
     * Format description into target JSON schema.
     *
     * @param jsonSchema Target JSON schema
     * @param description Description text
     * @param example     Optional example
     * @return Formatted description
     */
    public Map<String, Object> format(Map<String, Object> jsonSchema, String description, String example) {
        String prompt = buildFormatPrompt(jsonSchema, description);

        try {
            String response = RitsUtils.getRitsResponse(
                    "gpt-5.2",
                    prompt,
                    llmApiKey
            );
            return parseJsonResponse(response);
        } catch (Exception e) {
            Loggers.AGENT.error("Format failed: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Process description through multiple steps.
     *
     * @param data    Description data
     * @param oriTool Original tool description
     * @param steps   Processing steps
     * @return Processed description
     */
    public Map<String, Object> process(Map<String, Object> data, String oriTool, List<String> steps) {
        Map<String, Object> result = data;

        for (String step : steps) {
            switch (step) {
                case "cross_check":
                    result = crossCheck(result, oriTool);
                    break;
                case "clean":
                    result = cleanAndDeduplicate(result);
                    break;
                case "translate":
                    result = translateToChinese(result);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown processing step: " + step);
            }
        }
        return result;
    }

    /**
     * Clean and deduplicate description.
     *
     * @param data Description data
     * @return Cleaned description
     */
    public Map<String, Object> cleanAndDeduplicate(Map<String, Object> data) {
        String prompt = buildCleanPrompt(data);

        try {
            String response = RitsUtils.getRitsResponse(
                    evalModelId,
                    prompt,
                    llmApiKey
            );
            return parseJsonResponse(response);
        } catch (Exception e) {
            Loggers.AGENT.error("Clean failed: {}", e.getMessage());
            return data;
        }
    }

    /**
     * Cross-check with original description.
     *
     * @param data    Modified description
     * @param oriTool Original tool description
     * @return Cross-checked description
     */
    public Map<String, Object> crossCheck(Map<String, Object> data, String oriTool) {
        String prompt = buildCrossCheckPrompt(data, oriTool);

        try {
            String response = RitsUtils.getRitsResponse(
                    evalModelId,
                    prompt,
                    llmApiKey
            );
            return parseJsonResponse(response);
        } catch (Exception e) {
            Loggers.AGENT.error("Cross-check failed: {}", e.getMessage());
            return data;
        }
    }

    /**
     * Translate to Chinese if mostly English.
     *
     * @param data Description data
     * @return Translated description
     */
    public Map<String, Object> translateToChinese(Map<String, Object> data) {
        try {
            String jsonStr = OBJECT_MAPPER.writeValueAsString(data);

            if (!isMostlyEnglish(jsonStr)) {
                return data;
            }

            String prompt = buildTranslatePrompt(data);
            String response = RitsUtils.getRitsResponse(
                    evalModelId,
                    prompt,
                    llmApiKey
            );
            return parseJsonResponse(response);
        } catch (Exception e) {
            Loggers.AGENT.error("Translation failed: {}", e.getMessage());
            return data;
        }
    }

    private boolean isMostlyEnglish(String text) {
        String noSpace = text.replaceAll("\\s+", "");
        if (noSpace.isEmpty()) {
            return false;
        }

        long englishChars = noSpace.chars()
                .filter(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))
                .count();

        return (double) englishChars / noSpace.length() > 0.7;
    }

    private String buildFormatPrompt(Map<String, Object> jsonSchema, String description) {
        try {
            return String.format("""
将下面输入转换为目标 JSON 结构。必须满足：

- 输出只允许是有效 JSON，且严格匹配目标结构的键路径与层级（不多不少）。
- 语义必须完全保留：不新增、不删减、不改写含义；可改写措辞以压缩。
- description 去冗余是强制要求：
    - 任何 "每项包含/含有/由…组成/字段包括…" 这类字段清单式描述都必须删除或改写为非清单表述。
    - 不得在 description 中重复 schema 已表达的信息：字段名、字段类型、required 已涵盖的"必填"。
    - 枚举值列表只出现一次，放在最贴近字段的位置。
如输入中 description 同时包含"字段清单 + 业务约束"，只保留业务约束部分。

这是目标的json 模板:
%s

Input:
%s
""", OBJECT_MAPPER.writeValueAsString(jsonSchema), description);
        } catch (Exception e) {
            return "";
        }
    }

    private String buildCleanPrompt(Map<String, Object> data) {
        try {
            return String.format("""
Given a tool description JSON, go through the content sentence by sentence and perform cleaning tasks:

1. Remove usage example in the main tool description
2. Remove redundant "必填"/"可选"/"required"/"optional" markers
3. Remove verbose, redundant descriptions
4. Clean up descriptions

Keep only unique, essential, and actionable information.

Input JSON:
%s
""", OBJECT_MAPPER.writeValueAsString(data));
        } catch (Exception e) {
            return "";
        }
    }

    private String buildCrossCheckPrompt(Map<String, Object> data, String oriTool) {
        try {
            return String.format("""
比较原始描述和修改后的描述，按照以下要求整理修改后的描述：
1. 补充修改后的描述丢失的信息
2. 确保参数描述信息和工具描述信息位置正确

原始描述：
%s

修改后描述（待优化）：
%s
""", oriTool, OBJECT_MAPPER.writeValueAsString(data));
        } catch (Exception e) {
            return "";
        }
    }

    private String buildTranslatePrompt(Map<String, Object> data) {
        try {
            return String.format("""
Translate all English text in the following JSON to Chinese.
Keep JSON structure unchanged. Keep technical terms and code examples as-is.
Output only the translated JSON without explanations.

Input JSON:
%s
""", OBJECT_MAPPER.writeValueAsString(data));
        } catch (Exception e) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonResponse(String response) {
        try {
            return OBJECT_MAPPER.readValue(response, Map.class);
        } catch (Exception e) {
            Loggers.AGENT.error("Failed to parse JSON response: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
