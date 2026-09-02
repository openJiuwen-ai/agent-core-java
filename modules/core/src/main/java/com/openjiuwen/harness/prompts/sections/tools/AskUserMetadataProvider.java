/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ask-user tool metadata provider.
 * <p>
 * Aligned with Python openjiuwen's harness.prompts.tools.ask_user.
 *
 * @since 0.1.7
 */
public final class AskUserMetadataProvider implements ToolMetadataProvider {
    private static final String DESCRIPTION_CN = "向用户提问以收集信息、澄清歧义或做出决策。支持1-4个问题，每个问题2-4个选项。"
            + "\n\n"
            + "何时主动使用：需求模糊、多种方案可选、涉及用户偏好时，应主动询问而非假设。"
            + "\n\n"
            + "【禁止】选项中添加'其他'、'自定义'等兜底选项，系统已自动提供。"
            + "【推荐】将推荐选项放第一位，label末尾加'（推荐）'。"
            + "preview字段仅用于单选问题的视觉比较场景。";

    private static final String DESCRIPTION_EN = "Ask user questions to gather info, clarify ambiguity, "
            + "or make decisions. "
            + "Supports 1-4 questions, each with 2-4 options."
            + "\n\n"
            + "When to use proactively: Ask when requirements are vague, multiple approaches exist, "
            + "or user preferences matter. Don't assume."
            + "\n\n"
            + "FORBIDDEN: Adding 'Other', 'Custom' etc. as options — system provides this automatically. "
            + "RECOMMENDED: Place recommended option first, append '(Recommended)' to its label. "
            + "Preview field is only for single-select questions with visual comparison needs.";

    /**
     * getName.
     *
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getName() {
        return "ask_user";
    }

    /**
     * getDescription.
     *
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getDescription(String language) {
        return ToolSchemaSupport.localized(language, DESCRIPTION_CN, DESCRIPTION_EN);
    }

    /**
     * getInputParams.
     *
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> getInputParams(String language) {
        Map<String, Object> optionProperties = new LinkedHashMap<>();
        optionProperties.put("label",
                ToolSchemaSupport.property("string", text(language, "选项显示文本（1-5个词）",
                        "The display text for this option (1-5 words).")));
        optionProperties.put("description",
                ToolSchemaSupport.property("string", text(language, "选项详细说明",
                        "Explanation of what this option means or what will happen if chosen.")));
        optionProperties.put("preview",
                ToolSchemaSupport.property("string",
                        text(language, "可选的预览内容，用于UI模型、代码片段或视觉比较。仅在单选问题中支持。",
                            "Optional preview content rendered when this option is focused. Use for mockups, "
                            + "code snippets, or visual comparisons. Only supported for single-select questions.")));

        Map<String, Object> optionItem = new LinkedHashMap<>();
        optionItem.put("type", "object");
        optionItem.put("properties", optionProperties);
        optionItem.put("required", List.of("label", "description"));

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("type", "array");
        options.put("description", text(language, "可选答案列表（2-4个）",
                "Available choices for this question (2-4 options)"));
        options.put("items", optionItem);

        Map<String, Object> questionProperties = new LinkedHashMap<>();
        questionProperties.put("header",
                ToolSchemaSupport.property("string",
                        text(language, "问题的简短标题或标签",
                                "A short label or tag for the question (max 12 chars)")));
        questionProperties.put("question",
                ToolSchemaSupport.property("string", text(language, "完整的问题文本",
                        "The complete question to ask")));
        questionProperties.put("options", options);
        Map<String, Object> multiSelect = new LinkedHashMap<>(
                ToolSchemaSupport.property("boolean", text(language, "是否允许多选",
                        "Set to true to allow the user to select multiple options instead of just one.")));
        multiSelect.put("default", false);
        questionProperties.put("multi_select", multiSelect);

        Map<String, Object> questionItem = new LinkedHashMap<>();
        questionItem.put("type", "object");
        questionItem.put("properties", questionProperties);
        questionItem.put("required", List.of("header", "question", "options"));

        Map<String, Object> questions = new LinkedHashMap<>();
        questions.put("type", "array");
        questions.put("description", text(language, "向用户提出的问题列表（1-4个）",
                "Questions to ask the user (1-4 questions)"));
        questions.put("items", questionItem);
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

    private static String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
