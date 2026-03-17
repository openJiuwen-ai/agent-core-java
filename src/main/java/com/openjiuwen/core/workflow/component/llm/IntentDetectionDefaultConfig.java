/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Default configuration for IntentDetection component including prompt templates.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.intent_detection_comp.IntentDetectionDefaultConfig}.
 */
public class IntentDetectionDefaultConfig {

    private static final String DEFAULT_SYSTEM_PROMPT_ZH = "你是一个识别用户输入意图的AI助手。";
    private static final String DEFAULT_SYSTEM_PROMPT_EN = "You are an AI assistant that identifies user input intent.";

    private static final String DEFAULT_USER_PROMPT_ZH = """
            {{user_prompt}}

            当前可供选择的功能分类如下：
            {{category_info}}

            用户与助手的对话历史：
            {{chat_history}}

            当前输入：
            {{input}}

            请根据当前输入和对话历史分析并输出最适合的功能分类。输出格式为 JSON，包含以下两个字段：
            class: 代表分类结果
            reason: 说明为何选择该分类
            例如: {"class": "分类xx", "reason": "当前输入xxx"}
            请参考以下示例：
            {{example_content}}
            如果没有合适的分类，请输出 {{default_class}}。
            """;

    private static final String DEFAULT_USER_PROMPT_EN = """
            {{user_prompt}}

            Available function categories:
            {{category_info}}

            Conversation history between user and assistant:
            {{chat_history}}

            Current input:
            {{input}}

            Please analyze the current input and conversation history, and output the most suitable function category. Output format is JSON with the following two fields:
            class: represents the classification result
            reason: explains why this classification was chosen
            Example: {"class": "Category1", "reason": "Current input xxx"}
            Please refer to the following examples:
            {{example_content}}
            If no suitable category exists, output {{default_class}}.
            """;

    private List<String> categoryList = new ArrayList<>();
    private PromptTemplate intentDetectionTemplate;
    private String defaultClass;
    private boolean enableInput = true;

    public IntentDetectionDefaultConfig(String acceptLanguage) {
        this.intentDetectionTemplate = getDefaultTemplate(acceptLanguage);
        this.defaultClass = "en".equals(acceptLanguage) ? "Category0" : "分类0";
    }

    /**
     * Create a default intent detection template based on language.
     */
    public static PromptTemplate getDefaultTemplate(String acceptLanguage) {
        List<BaseMessage> content = new ArrayList<>();
        if ("en".equals(acceptLanguage)) {
            content.add(SystemMessage.builder().content(DEFAULT_SYSTEM_PROMPT_EN).build());
            content.add(UserMessage.builder().content(DEFAULT_USER_PROMPT_EN).build());
        } else {
            content.add(SystemMessage.builder().content(DEFAULT_SYSTEM_PROMPT_ZH).build());
            content.add(UserMessage.builder().content(DEFAULT_USER_PROMPT_ZH).build());
        }
        return PromptTemplate.builder().content(content).build();
    }

    public List<String> getCategoryList() {
        return categoryList;
    }

    public void setCategoryList(List<String> categoryList) {
        this.categoryList = categoryList;
    }

    public PromptTemplate getIntentDetectionTemplate() {
        return intentDetectionTemplate;
    }

    public void setIntentDetectionTemplate(PromptTemplate intentDetectionTemplate) {
        this.intentDetectionTemplate = intentDetectionTemplate;
    }

    public String getDefaultClass() {
        return defaultClass;
    }

    public void setDefaultClass(String defaultClass) {
        this.defaultClass = defaultClass;
    }

    public boolean isEnableInput() {
        return enableInput;
    }

    public void setEnableInput(boolean enableInput) {
        this.enableInput = enableInput;
    }
}
