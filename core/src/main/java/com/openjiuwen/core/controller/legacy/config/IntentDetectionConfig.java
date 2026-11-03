/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Config of Intent Detection Component.
 *
 * <p>Mirrors Python's {@code IntentDetectionConfig} in
 * {@code openjiuwen/core/controller/legacy/config/reasoner_config.py}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentDetectionConfig {

    public static final String DEFAULT_SYSTEM_PROMPT = String.join("\n",
            "你是一个意图分类助手，擅长判断用户的输入属于哪个分类。",
            "当用户输入没有明确意图或者你无法判断用户输入意图时请选择 {{default_class}}。",
            "以下是给定的意图分类列表：",
            "{{category_list}}",
            "{{example_content}}",
            "请根据上述要求判断用户输入意图分类，输出要求如下：",
            "直接以JSON格式输出分类ID，不进行任何解释。JSON格式如下：",
            " {\"result\": int}");

    public static final String DEFAULT_USER_PROMPT = String.join("\n",
            "用户与助手的对话历史：",
            "{{chat_history}}",
            "当前输入：",
            "{{input}}");

    @Builder.Default
    @JsonProperty("category_info")
    private String categoryInfo = "";

    @Builder.Default
    @JsonProperty("category_list")
    private List<String> categoryList = new ArrayList<>();

    @Builder.Default
    @JsonProperty("intent_detection_template")
    private PromptTemplate intentDetectionTemplate = getDefaultTemplate();

    @Builder.Default
    @JsonProperty("user_prompt")
    private String userPrompt = DEFAULT_USER_PROMPT;

    @Builder.Default
    @JsonProperty("chat_history_max_turn")
    private int chatHistoryMaxTurn = 100;

    @Builder.Default
    @JsonProperty("default_class")
    private String defaultClass = "分类0";

    @Builder.Default
    @JsonProperty("enable_history")
    private boolean enableHistory = false;

    @Builder.Default
    @JsonProperty("enable_input")
    private boolean enableInput = true;

    @Builder.Default
    @JsonProperty("example_content")
    private List<String> exampleContent = new ArrayList<>();

    public static PromptTemplate getDefaultTemplate() {
        return PromptTemplate.builder()
                .content(List.of(
                        new SystemMessage(DEFAULT_SYSTEM_PROMPT),
                        new UserMessage(DEFAULT_USER_PROMPT)
                ))
                .build();
    }

    public void setCategoryInfo(String categoryInfo) {
        this.categoryInfo = categoryInfo == null ? "" : categoryInfo;
    }

    public void setCategoryList(List<String> categoryList) {
        this.categoryList = categoryList == null ? new ArrayList<>() : new ArrayList<>(categoryList);
    }

    public void setIntentDetectionTemplate(PromptTemplate intentDetectionTemplate) {
        this.intentDetectionTemplate = intentDetectionTemplate == null ? getDefaultTemplate() : intentDetectionTemplate;
    }

    public void setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt == null ? DEFAULT_USER_PROMPT : userPrompt;
    }

    public void setDefaultClass(String defaultClass) {
        this.defaultClass = defaultClass == null ? "分类0" : defaultClass;
    }

    public void setExampleContent(List<String> exampleContent) {
        this.exampleContent = exampleContent == null ? new ArrayList<>() : new ArrayList<>(exampleContent);
    }
}
