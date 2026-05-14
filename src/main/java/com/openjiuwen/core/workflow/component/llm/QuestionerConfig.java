/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.workflow.component.ComponentConfig;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the Questioner workflow component.
 * <p>
 * Mirrors Python's {@code QuestionerConfig}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionerConfig extends ComponentConfig {

    private String modelId;
    private ModelClientConfig modelClientConfig;
    private ModelRequestConfig modelConfig;
    private String responseType = ResponseType.REPLY_DIRECTLY.getValue();
    private String questionContent = "";
    private boolean extractFieldsFromResponse = true;
    private List<FieldInfo> fieldNames = new ArrayList<>();
    private int maxResponse = 3;
    private boolean withChatHistory = false;
    private int chatHistoryMaxRounds = 5;
    private String extraPromptForFieldsExtraction = "";
    private String exampleContent = "";
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

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public String getQuestionContent() {
        return questionContent;
    }

    public void setQuestionContent(String questionContent) {
        this.questionContent = questionContent;
    }

    public boolean isExtractFieldsFromResponse() {
        return extractFieldsFromResponse;
    }

    public void setExtractFieldsFromResponse(boolean extractFieldsFromResponse) {
        this.extractFieldsFromResponse = extractFieldsFromResponse;
    }

    public List<FieldInfo> getFieldNames() {
        return fieldNames;
    }

    public void setFieldNames(List<FieldInfo> fieldNames) {
        this.fieldNames = fieldNames;
    }

    public int getMaxResponse() {
        return maxResponse;
    }

    public void setMaxResponse(int maxResponse) {
        this.maxResponse = maxResponse;
    }

    public boolean isWithChatHistory() {
        return withChatHistory;
    }

    public void setWithChatHistory(boolean withChatHistory) {
        this.withChatHistory = withChatHistory;
    }

    public int getChatHistoryMaxRounds() {
        return chatHistoryMaxRounds;
    }

    public void setChatHistoryMaxRounds(int chatHistoryMaxRounds) {
        this.chatHistoryMaxRounds = chatHistoryMaxRounds;
    }

    public String getExtraPromptForFieldsExtraction() {
        return extraPromptForFieldsExtraction;
    }

    public void setExtraPromptForFieldsExtraction(String extraPromptForFieldsExtraction) {
        this.extraPromptForFieldsExtraction = extraPromptForFieldsExtraction;
    }

    public String getExampleContent() {
        return exampleContent;
    }

    public void setExampleContent(String exampleContent) {
        this.exampleContent = exampleContent;
    }

    public String getAcceptLanguage() {
        return acceptLanguage;
    }

    public void setAcceptLanguage(String acceptLanguage) {
        this.acceptLanguage = acceptLanguage;
    }
}
