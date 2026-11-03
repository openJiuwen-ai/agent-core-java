/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy intent detection configuration.
 *
 * <p>Mirrors Python's {@code IntentDetectionConfig} in
 * {@code openjiuwen/core/single_agent/legacy/config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntentDetectionConfig {
    @JsonProperty("intent_detection_template")
    private List<Map<String, Object>> intentDetectionTemplate = new ArrayList<>();

    @JsonProperty("default_class")
    private String defaultClass = "\u5206\u7c7b1";

    @JsonProperty("enable_input")
    private boolean enableInput = true;

    @JsonProperty("enable_history")
    private boolean enableHistory = false;

    @JsonProperty("chat_history_max_turn")
    private int chatHistoryMaxTurn = 5;

    @JsonProperty("category_list")
    private List<String> categoryList = new ArrayList<>();

    @JsonProperty("user_prompt")
    private String userPrompt = "";

    @JsonProperty("example_content")
    private List<String> exampleContent = new ArrayList<>();

    public List<Map<String, Object>> getIntentDetectionTemplate() {
        return intentDetectionTemplate;
    }

    public void setIntentDetectionTemplate(List<Map<String, Object>> intentDetectionTemplate) {
        this.intentDetectionTemplate = copyPrompt(intentDetectionTemplate);
    }

    public String getDefaultClass() {
        return defaultClass;
    }

    public void setDefaultClass(String defaultClass) {
        this.defaultClass = defaultClass == null ? "" : defaultClass;
    }

    public boolean isEnableInput() {
        return enableInput;
    }

    public void setEnableInput(boolean enableInput) {
        this.enableInput = enableInput;
    }

    public boolean isEnableHistory() {
        return enableHistory;
    }

    public void setEnableHistory(boolean enableHistory) {
        this.enableHistory = enableHistory;
    }

    public int getChatHistoryMaxTurn() {
        return chatHistoryMaxTurn;
    }

    public void setChatHistoryMaxTurn(int chatHistoryMaxTurn) {
        this.chatHistoryMaxTurn = chatHistoryMaxTurn;
    }

    public List<String> getCategoryList() {
        return categoryList;
    }

    public void setCategoryList(List<String> categoryList) {
        this.categoryList = categoryList == null ? new ArrayList<>() : new ArrayList<>(categoryList);
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public void setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt == null ? "" : userPrompt;
    }

    public List<String> getExampleContent() {
        return exampleContent;
    }

    public void setExampleContent(List<String> exampleContent) {
        this.exampleContent = exampleContent == null ? new ArrayList<>() : new ArrayList<>(exampleContent);
    }

    private static List<Map<String, Object>> copyPrompt(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (source != null) {
            for (Map<String, Object> item : source) {
                copy.add(item == null ? new LinkedHashMap<>() : new LinkedHashMap<>(item));
            }
        }
        return copy;
    }
}
