/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import com.openjiuwen.core.foundation.llm.Model;

/**
 * Mirrors Python's {@code MemoryOperationParams} in
 * {@code openjiuwen/core/memory/process/extract/common.py}.
 */
public class MemoryOperationParams {

    private String userId;
    private String scopeId;
    private String messageMemId;
    private String timestamp;
    private Model baseChatModel;
    private Object semanticStore;

    public MemoryOperationParams(String userId,
                                 String scopeId,
                                 String messageMemId,
                                 String timestamp,
                                 Model baseChatModel,
                                 Object semanticStore) {
        this.userId = userId;
        this.scopeId = scopeId;
        this.messageMemId = messageMemId;
        this.timestamp = timestamp;
        this.baseChatModel = baseChatModel;
        this.semanticStore = semanticStore;
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

    public String getMessageMemId() {
        return messageMemId;
    }

    public void setMessageMemId(String messageMemId) {
        this.messageMemId = messageMemId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Model getBaseChatModel() {
        return baseChatModel;
    }

    public void setBaseChatModel(Model baseChatModel) {
        this.baseChatModel = baseChatModel;
    }

    public Object getSemanticStore() {
        return semanticStore;
    }

    public void setSemanticStore(Object semanticStore) {
        this.semanticStore = semanticStore;
    }
}
