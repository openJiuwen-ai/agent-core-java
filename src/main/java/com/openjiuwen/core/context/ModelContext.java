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
public class ModelContext implements com.openjiuwen.core.context_engine.ModelContext {
    private final com.openjiuwen.core.context_engine.ModelContext delegate;

    protected ModelContext(com.openjiuwen.core.context_engine.ModelContext delegate) {
        this.delegate = delegate;
    }

    public static ModelContext wrap(com.openjiuwen.core.context_engine.ModelContext context) {
        if (context instanceof ModelContext modelContext) {
            return modelContext;
        }
        return context == null ? null : new ModelContext(context);
    }

    public static com.openjiuwen.core.context_engine.ModelContext unwrap(ModelContext context) {
        return context == null ? null : context.unwrap();
    }

    public com.openjiuwen.core.context_engine.ModelContext unwrap() {
        return delegate;
    }

    public int size() {
        return delegate.length();
    }

    @Override
    public int length() {
        return delegate.length();
    }

    @Override
    public List<BaseMessage> getMessages(Integer size, boolean withHistory) {
        return delegate.getMessages(size, withHistory);
    }

    public List<BaseMessage> getMessages() {
        return getMessages(null, true);
    }

    @Override
    public void setMessages(List<BaseMessage> messages, boolean withHistory) {
        delegate.setMessages(messages, withHistory);
    }

    public void setMessages(List<BaseMessage> messages) {
        setMessages(messages, true);
    }

    @Override
    public List<BaseMessage> popMessages(int size, boolean withHistory) {
        return delegate.popMessages(size, withHistory);
    }

    public List<BaseMessage> popMessages() {
        return popMessages(1, true);
    }

    @Override
    public CompletionStage<Void> clearMessages(boolean withHistory) {
        CompletionStage<Void> stage = delegate.clearMessages(withHistory);
        await(stage);
        return stage;
    }

    public CompletionStage<Void> clearMessages() {
        return clearMessages(true);
    }

    @Override
    public CompletionStage<List<BaseMessage>> addMessages(List<BaseMessage> messages) {
        CompletionStage<List<BaseMessage>> stage = delegate.addMessages(messages);
        await(stage);
        return stage;
    }

    @Override
    public CompletionStage<List<BaseMessage>> addMessages(BaseMessage message) {
        CompletionStage<List<BaseMessage>> stage = delegate.addMessages(message);
        await(stage);
        return stage;
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

    @Override
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

    @Override
    public com.openjiuwen.core.context_engine.ContextStats statistic() {
        return delegate.statistic();
    }

    @Override
    public String sessionId() {
        return delegate.sessionId();
    }

    @Override
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

    @Override
    public com.openjiuwen.core.context_engine.ModelContext.TokenCounterPort tokenCounter() {
        return delegate.tokenCounter();
    }

    @Override
    public com.openjiuwen.core.context_engine.ModelContext.ToolPort reloaderTool() {
        return delegate.reloaderTool();
    }

    private static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }
}
