/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Parameters for memory extraction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractMemoryParams {
    private String userId;
    private String scopeId;
    private List<BaseMessage> messages;
    private List<BaseMessage> historyMessages;
    /**
     * Tuple: (modelName, modelClient)
     */
    private Map.Entry<String, Model> baseChatModel;

    public static ExtractMemoryParamsBuilder builder() {
        return new ExtractMemoryParamsBuilder();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public List<BaseMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<BaseMessage> messages) {
        this.messages = messages;
    }

    public List<BaseMessage> getHistoryMessages() {
        return historyMessages;
    }

    public void setHistoryMessages(List<BaseMessage> historyMessages) {
        this.historyMessages = historyMessages;
    }

    public Map.Entry<String, Model> getBaseChatModel() {
        return baseChatModel;
    }

    public void setBaseChatModel(Map.Entry<String, Model> baseChatModel) {
        this.baseChatModel = baseChatModel;
    }

    public static final class ExtractMemoryParamsBuilder {
        private String userId;
        private String scopeId;
        private List<BaseMessage> messages;
        private List<BaseMessage> historyMessages;
        private Map.Entry<String, Model> baseChatModel;

        public ExtractMemoryParamsBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public ExtractMemoryParamsBuilder scopeId(String scopeId) {
            this.scopeId = scopeId;
            return this;
        }

        public ExtractMemoryParamsBuilder messages(List<BaseMessage> messages) {
            this.messages = messages;
            return this;
        }

        public ExtractMemoryParamsBuilder historyMessages(List<BaseMessage> historyMessages) {
            this.historyMessages = historyMessages;
            return this;
        }

        public ExtractMemoryParamsBuilder baseChatModel(Map.Entry<String, Model> baseChatModel) {
            this.baseChatModel = baseChatModel;
            return this;
        }

        public ExtractMemoryParams build() {
            return new ExtractMemoryParams(userId, scopeId, messages, historyMessages, baseChatModel);
        }
    }
}
