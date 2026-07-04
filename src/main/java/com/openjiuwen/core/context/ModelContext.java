/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Synchronous compatibility wrapper for the pre-0.1.14 root context package.
 *
 * <p>Mirrors Python's {@code ModelContext} in
 * {@code openjiuwen/core/context_engine/base.py}.</p>
 */
public class ModelContext {
    private final com.openjiuwen.core.context_engine.ModelContext delegate;

    ModelContext(com.openjiuwen.core.context_engine.ModelContext delegate) {
        this.delegate = delegate;
    }

    public com.openjiuwen.core.context_engine.ModelContext unwrap() {
        return delegate;
    }

    public int size() {
        return delegate.length();
    }

    public int length() {
        return delegate.length();
    }

    public List<BaseMessage> getMessages(Integer size, boolean withHistory) {
        return delegate.getMessages(size, withHistory);
    }

    public List<BaseMessage> getMessages() {
        return getMessages(null, true);
    }

    public void setMessages(List<BaseMessage> messages, boolean withHistory) {
        delegate.setMessages(messages, withHistory);
    }

    public void setMessages(List<BaseMessage> messages) {
        setMessages(messages, true);
    }

    public List<BaseMessage> popMessages(int size, boolean withHistory) {
        return delegate.popMessages(size, withHistory);
    }

    public List<BaseMessage> popMessages() {
        return popMessages(1, true);
    }

    public void clearMessages(boolean withHistory) {
        await(delegate.clearMessages(withHistory));
    }

    public void clearMessages() {
        clearMessages(true);
    }

    public List<BaseMessage> addMessages(List<BaseMessage> messages) {
        return await(delegate.addMessages(messages));
    }

    public List<BaseMessage> addMessages(BaseMessage message) {
        return await(delegate.addMessages(message));
    }

    public String compressContext(List<String> processorTypes, Map<String, Object> kwargs) {
        if (delegate instanceof com.openjiuwen.core.context_engine.context.SessionModelContext sessionModelContext) {
            Object result = await(sessionModelContext.compressContext(processorTypes, kwargs));
            return String.valueOf(result);
        }
        throw new UnsupportedOperationException("compressContext is not implemented");
    }

    public String compressContext() {
        return compressContext(null, Map.of());
    }

    public ContextWindow getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools, Integer windowSize,
                                          Integer dialogueRound, Map<String, Object> kwargs) {
        return ContextWindow.from(await(delegate.getContextWindow(systemMessages, tools, windowSize, dialogueRound,
                kwargs == null ? Map.of() : kwargs)));
    }

    public ContextWindow getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools, Integer windowSize,
                                          Integer dialogueRound) {
        return getContextWindow(systemMessages, tools, windowSize, dialogueRound, Map.of());
    }

    public ContextWindow getContextWindow() {
        return getContextWindow(null, null, null, null, Map.of());
    }

    public com.openjiuwen.core.context_engine.ContextStats statistic() {
        return delegate.statistic();
    }

    public String sessionId() {
        return delegate.sessionId();
    }

    public String contextId() {
        return delegate.contextId();
    }

    public String workspaceDir() {
        if (delegate instanceof com.openjiuwen.core.context_engine.context.SessionModelContext sessionModelContext) {
            return sessionModelContext.workspaceDir();
        }
        return "";
    }

    public Object sysOperation() {
        return null;
    }

    public Object tokenCounter() {
        return delegate.tokenCounter();
    }

    public Object reloaderTool() {
        return delegate.reloaderTool();
    }

    private static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }
}
