/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.List;

/**
 * Mirrors Python's {@code ExtractMemoryParams} in
 * {@code openjiuwen/core/memory/process/extract/common.py}.
 */
public class ExtractMemoryParams {

    private String userId;
    private String scopeId;
    private List<BaseMessage> messages;
    private List<BaseMessage> historyMessages;
    private Model baseChatModel;

    public ExtractMemoryParams(String userId,
                               String scopeId,
                               List<BaseMessage> messages,
                               List<BaseMessage> historyMessages,
                               Model baseChatModel) {
        this.userId = userId;
        this.scopeId = scopeId;
        this.messages = messages;
        this.historyMessages = historyMessages;
        this.baseChatModel = baseChatModel;
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

    public Model getBaseChatModel() {
        return baseChatModel;
    }

    public void setBaseChatModel(Model baseChatModel) {
        this.baseChatModel = baseChatModel;
    }
}
