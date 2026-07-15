/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.llm;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Alias/extension of {@link com.openjiuwen.core.workflow.component.llm.QuestionerConfig}
 * with positional constructor for test compatibility.
 * 
 * @since 0.1.7
 */
public class QuestionerConfig extends com.openjiuwen.core.workflow.component.llm.QuestionerConfig {
    /**
     * QuestionerConfig.
     * 
     * @param modelConfig modelConfig
     * @param modelClientConfig modelClientConfig
     * @param questionContent questionContent
     * @param extractFieldsFromResponse extractFieldsFromResponse
     * @param fieldNames fieldNames
     * @param withChatHistory withChatHistory
     * @since 0.1.7
     */
    public QuestionerConfig(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig, String questionContent,
            boolean extractFieldsFromResponse, List<?> fieldNames, boolean withChatHistory) {
        super();
        setModelConfig(modelConfig);
        setModelClientConfig(modelClientConfig);
        setQuestionContent(questionContent != null ? questionContent : "");
        setExtractFieldsFromResponse(extractFieldsFromResponse);
        setWithChatHistory(withChatHistory);
        if (fieldNames != null) {
            List<com.openjiuwen.core.workflow.component.llm.FieldInfo> converted = fieldNames.stream().map(fi -> {
                if (fi instanceof com.openjiuwen.core.workflow.component.llm.FieldInfo base) {
                    return base;
                }
                return new com.openjiuwen.core.workflow.component.llm.FieldInfo();
            }).collect(Collectors.toList());
            setFieldNames(converted);
        } else {
            setFieldNames(List.of());
        }
    }

    /**
     * QuestionerConfig.
     * 
     * @since 0.1.7
     */
    public QuestionerConfig() {
        super();
    }

    /**
     * Snake_case aliases for test compatibility (mirrors Python attribute names).
     * 
     * @param modelConfig modelConfig
     * @since 0.1.7
     */
    public void setModel_config(ModelRequestConfig modelConfig) {
        setModelConfig(modelConfig);
    }

    /**
     * setModel_client_config.
     * 
     * @param modelClientConfig modelClientConfig
     * @since 0.1.7
     */
    public void setModel_client_config(ModelClientConfig modelClientConfig) {
        setModelClientConfig(modelClientConfig);
    }

    /**
     * setWith_chat_history.
     * 
     * @param withChatHistory withChatHistory
     * @since 0.1.7
     */
    public void setWith_chat_history(boolean withChatHistory) {
        setWithChatHistory(withChatHistory);
    }

    /**
     * setField_names.
     * 
     * @param fieldNames fieldNames
     * @since 0.1.7
     */
    public void setField_names(List<?> fieldNames) {
        if (fieldNames == null) {
            setFieldNames(java.util.List.of());
            return;
        }
        java.util.List<com.openjiuwen.core.workflow.component.llm.FieldInfo> converted =
            fieldNames.stream().map(fi -> (com.openjiuwen.core.workflow.component.llm.FieldInfo) fi)
                    .collect(java.util.stream.Collectors.toList());
        setFieldNames(converted);
    }
}
