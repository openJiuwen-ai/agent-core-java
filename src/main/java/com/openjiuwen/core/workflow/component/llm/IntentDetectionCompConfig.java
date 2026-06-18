/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration values used by intent-detection components.
 *
 * <p>Mirrors Python's {@code IntentDetectionCompConfig} in
 * {@code openjiuwen/core/workflow/components/llm/intent_detection_comp.py}.</p>
 */
public class IntentDetectionCompConfig {

    private String modelId;
    private ModelClientConfig modelClientConfig;
    private ModelRequestConfig modelConfig;
    private final List<String> categoryNameList = new ArrayList<>();
    private String userPrompt = "";
    private final List<String> exampleContent = new ArrayList<>();
    private boolean enableHistory;
    private int chatHistoryMaxTurn = 3;
    private String acceptLanguage = "zh";

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public ModelClientConfig getModelClientConfig() {
        return modelClientConfig;
    }

    public void setModelClientConfig(ModelClientConfig modelClientConfig) {
        this.modelClientConfig = modelClientConfig;
    }

    public ModelRequestConfig getModelConfig() {
        return modelConfig;
    }

    public void setModelConfig(ModelRequestConfig modelConfig) {
        this.modelConfig = modelConfig;
    }

    public List<String> getCategoryNameList() {
        return categoryNameList;
    }

    public void setCategoryNameList(List<String> categoryNameList) {
        this.categoryNameList.clear();
        if (categoryNameList != null) {
            this.categoryNameList.addAll(categoryNameList);
        }
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

    public String getAcceptLanguage() {
        return acceptLanguage;
    }

    public void setAcceptLanguage(String acceptLanguage) {
        this.acceptLanguage = acceptLanguage == null || acceptLanguage.isEmpty() ? "zh" : acceptLanguage;
    }
}
