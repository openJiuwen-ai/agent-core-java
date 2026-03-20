// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.workflow.components.llm;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Alias/extension of {@link com.openjiuwen.core.workflow.component.llm.QuestionerConfig}
 * with positional constructor for test compatibility.
 */
public class QuestionerConfig extends com.openjiuwen.core.workflow.component.llm.QuestionerConfig {

    /**
     * Positional constructor matching Python test usage:
     * QuestionerConfig(modelCfg, modelClientCfg, questionContent, extractFieldsFromResponse, fieldNames, withChatHistory)
     * fieldNames can be List of our FieldInfo subclass or base FieldInfo.
     */
    public QuestionerConfig(
            ModelRequestConfig modelConfig,
            ModelClientConfig modelClientConfig,
            String questionContent,
            boolean extractFieldsFromResponse,
            List<?> fieldNames,
            boolean withChatHistory) {
        super();
        setModelConfig(modelConfig);
        setModelClientConfig(modelClientConfig);
        setQuestionContent(questionContent != null ? questionContent : "");
        setExtractFieldsFromResponse(extractFieldsFromResponse);
        setWithChatHistory(withChatHistory);
        if (fieldNames != null) {
            List<com.openjiuwen.core.workflow.component.llm.FieldInfo> converted =
                    fieldNames.stream()
                            .map(fi -> {
                                if (fi instanceof com.openjiuwen.core.workflow.component.llm.FieldInfo base) {
                                    return base;
                                }
                                return new com.openjiuwen.core.workflow.component.llm.FieldInfo();
                            })
                            .collect(Collectors.toList());
            setFieldNames(converted);
        } else {
            setFieldNames(List.of());
        }
    }

    public QuestionerConfig() {
        super();
    }

    /** Snake_case aliases for test compatibility (mirrors Python attribute names). */
    public void setModel_config(ModelRequestConfig modelConfig) { setModelConfig(modelConfig); }
    public void setModel_client_config(ModelClientConfig modelClientConfig) { setModelClientConfig(modelClientConfig); }
    public void setWith_chat_history(boolean withChatHistory) { setWithChatHistory(withChatHistory); }
    public void setField_names(List<?> fieldNames) {
        if (fieldNames == null) {
            setFieldNames(java.util.List.of());
            return;
        }
        java.util.List<com.openjiuwen.core.workflow.component.llm.FieldInfo> converted =
            fieldNames.stream()
                .map(fi -> (com.openjiuwen.core.workflow.component.llm.FieldInfo) fi)
                .collect(java.util.stream.Collectors.toList());
        setFieldNames(converted);
    }
}
