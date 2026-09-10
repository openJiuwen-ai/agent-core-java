/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code AskUserMetadataProvider} in
 * {@code openjiuwen/harness/prompts/tools/ask_user.py}.
 */
public final class AskUserMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTION = Map.of(
            "cn",
            """
            向用户提问以收集信息、澄清歧义或做出决策。支持1-4个问题，每个问题2-4个选项。

            何时主动使用：需求模糊、多种方案可选、涉及用户偏好时，应主动询问而非假设。

            【禁止】选项中添加'其他'、'自定义'等兜底选项，系统已自动提供。
            【推荐】将推荐选项放第一位，label末尾加'（推荐）'。
            preview字段仅用于单选问题的视觉比较场景。
            """,
            "en",
            """
            Ask user questions to gather info, clarify ambiguity, or make decisions. Supports 1-4 questions, each with 2-4 options.

            When to use proactively: Ask when requirements are vague, multiple approaches exist, or user preferences matter. Don't assume.

            FORBIDDEN: Adding 'Other', 'Custom' etc. as options — system provides this automatically.
            RECOMMENDED: Place recommended option first, append '(Recommended)' to its label.
            Preview field is only for single-select questions with visual comparison needs.
            """
    );

    private static final Map<String, Map<String, String>> ASK_USER_PARAMS = createParams();

    @Override
    public String getName() {
        return "ask_user";
    }

    @Override
    public String getDescription(String language) {
        return DESCRIPTION.getOrDefault(language, DESCRIPTION.get("cn"));
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return getAskUserInputParams(language);
    }

    public static Map<String, Object> getAskUserInputParams(String language) {
        String lang = "en".equals(language) ? "en" : "cn";

        Map<String, Object> optionProperties = new LinkedHashMap<>();
        optionProperties.put("label", property("string", ASK_USER_PARAMS.get("options_label").get(lang)));
        optionProperties.put("description", property("string", ASK_USER_PARAMS.get("options_description").get(lang)));
        optionProperties.put("preview", property("string", ASK_USER_PARAMS.get("options_preview").get(lang)));

        Map<String, Object> optionItem = new LinkedHashMap<>();
        optionItem.put("type", "object");
        optionItem.put("properties", optionProperties);
        optionItem.put("required", List.of("label", "description"));

        Map<String, Object> questionProperties = new LinkedHashMap<>();
        questionProperties.put("header", property("string", ASK_USER_PARAMS.get("header").get(lang)));
        questionProperties.put("question", property("string", ASK_USER_PARAMS.get("question").get(lang)));
        questionProperties.put("options", arrayProperty(
                ASK_USER_PARAMS.get("options").get(lang),
                optionItem
        ));

        Map<String, Object> multiSelect = property("boolean", ASK_USER_PARAMS.get("multi_select").get(lang));
        multiSelect.put("default", Boolean.FALSE);
        questionProperties.put("multi_select", multiSelect);

        Map<String, Object> questionItem = new LinkedHashMap<>();
        questionItem.put("type", "object");
        questionItem.put("properties", questionProperties);
        questionItem.put("required", List.of("header", "question", "options"));

        Map<String, Object> questions = arrayProperty(ASK_USER_PARAMS.get("questions").get(lang), questionItem);
        questions.put("minItems", 1);
        questions.put("maxItems", 4);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("questions", questions);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("questions"));
        return schema;
    }

    private static Map<String, Map<String, String>> createParams() {
        Map<String, Map<String, String>> params = new LinkedHashMap<>();
        params.put("questions", Map.of(
                "cn", "向用户提出的问题列表（1-4个）",
                "en", "Questions to ask the user (1-4 questions)"
        ));
        params.put("header", Map.of(
                "cn", "问题的简短标题或标签",
                "en", "A short label or tag for the question (max 12 chars)"
        ));
        params.put("question", Map.of(
                "cn", "完整的问题文本",
                "en", "The complete question to ask"
        ));
        params.put("options", Map.of(
                "cn", "可选答案列表（2-4个）",
                "en", "Available choices for this question (2-4 options)"
        ));
        params.put("options_label", Map.of(
                "cn", "选项显示文本（1-5个词）",
                "en", "The display text for this option (1-5 words)."
        ));
        params.put("options_description", Map.of(
                "cn", "选项详细说明",
                "en", "Explanation of what this option means or what will happen if chosen."
        ));
        params.put("options_preview", Map.of(
                "cn", "可选的预览内容，用于UI模型、代码片段或视觉比较。仅在单选问题中支持。",
                "en", "Optional preview content rendered when this option is focused. Use for mockups, code snippets, or visual comparisons. Only supported for single-select questions."
        ));
        params.put("multi_select", Map.of(
                "cn", "是否允许多选",
                "en", "Set to true to allow the user to select multiple options instead of just one."
        ));
        return params;
    }

    private static Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private static Map<String, Object> arrayProperty(String description, Object items) {
        Map<String, Object> property = property("array", description);
        property.put("items", items);
        return property;
    }
}
