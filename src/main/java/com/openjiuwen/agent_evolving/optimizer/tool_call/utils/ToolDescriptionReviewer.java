/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.function.Function;

/**
 * Tool description reviewer for cleaning and processing.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.customized_reviewer.ToolDescriptionReviewer}.
 */
public class ToolDescriptionReviewer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String evalModelId;
    private final String llmApiKey;
    private final List<Object> processors;

    /**
     * Create tool description reviewer.
     *
     * @param evalModelId Evaluation model ID
     * @param llmApiKey   LLM API key
     */
    public ToolDescriptionReviewer(String evalModelId, String llmApiKey) {
        this.evalModelId = evalModelId;
        this.llmApiKey = llmApiKey;
        this.processors = new ArrayList<>();
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
        return ensureMap(invokeRitsJson("gpt-5.2", prompt));
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
                    result = crossCheck(data, oriTool);
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
        return ensureMap(invokeRitsJson(evalModelId, prompt));
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
        return ensureMap(invokeRitsJson(evalModelId, prompt));
    }

    /**
     * Translate to Chinese if mostly English.
     *
     * @param data Description data
     * @return Translated description
     */
    public Map<String, Object> translateToChinese(Map<String, Object> data) {
        String jsonStr = toJson(data);

        if (!isMostlyEnglish(jsonStr)) {
            return data;
        }

        String prompt = buildTranslatePrompt(data);
        return ensureMap(invokeRitsJson(evalModelId, prompt));
    }

    protected boolean isMostlyEnglish(String text) {
        String noSpace = text.replaceAll("\\s+", "");
        if (noSpace.isEmpty()) {
            return false;
        }

        long englishChars = noSpace.chars()
                .filter(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))
                .count();

        return (double) englishChars / noSpace.length() > 0.7;
    }

    public List<Object> getProcessors() {
        return processors;
    }

    private String buildFormatPrompt(Map<String, Object> jsonSchema, String description) {
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
""", toJson(jsonSchema), description);
    }

    private String buildCleanPrompt(Map<String, Object> data) {
        return String.format("""
Given a tool description JSON, go through the content sentence by sentence and perform cleaning tasks:

1. Remove usage example in the main tool description
2. Remove redundant "必填"/"可选"/"required"/"optional" markers
3. Remove verbose, redundant descriptions
4. Clean up descriptions

Keep only unique, essential, and actionable information.

Input JSON:
%s
""", toJson(data));
    }

    private String buildCrossCheckPrompt(Map<String, Object> data, String oriTool) {
        return String.format("""
比较原始描述和修改后的描述，按照以下要求整理修改后的描述：
1. 补充修改后的描述丢失的信息
2. 确保参数描述信息和工具描述信息位置正确

原始描述：
%s

修改后描述（待优化）：
%s
""", oriTool, toJson(data));
    }

    private String buildTranslatePrompt(Map<String, Object> data) {
        return String.format("""
Translate all English text in the following JSON to Chinese.
Keep JSON structure unchanged. Keep technical terms and code examples as-is.
Output only the translated JSON without explanations.

Input JSON:
%s
""", toJson(data));
    }

    protected Object invokeRitsResponse(String modelId, String prompt, Function<String, Object> verifyFn) {
        return RitsUtils.getRitsResponse(
                modelId,
                prompt,
                llmApiKey,
                verifyFn,
                false,
                Map.of("max_attempts", 5, "include_stop_sequence", false)
        );
    }

    private Object invokeRitsJson(String modelId, String prompt) {
        return invokeRitsResponse(modelId, prompt, this::parseJsonResponse);
    }

    @SuppressWarnings("unchecked")
    private Object parseJsonResponse(String response) {
        try {
            return OBJECT_MAPPER.readValue(response, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse JSON response", e);
        }
    }

    private Map<String, Object> ensureMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize JSON", e);
        }
    }
}
