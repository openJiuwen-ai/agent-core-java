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
 * Abstract contract for managing conversational context.
 *
 * <p>Mirrors Python's {@code ModelContext} in
 * {@code openjiuwen/core/context_engine/base.py}.</p>
 */
public interface ModelContext {

    /** No-op compatibility helper after context/context_engine merge. */
    static ModelContext wrap(ModelContext context) {
        return context;
    }

    /** No-op compatibility helper after context/context_engine merge. */
    static ModelContext unwrap(ModelContext context) {
        return context;
    }

    /** No-op compatibility helper after context/context_engine merge. */
    default ModelContext unwrap() {
        return this;
    }

    int length();

    /** Compatibility alias for {@link #length()}. */
    default int size() {
        return length();
    }

    List<BaseMessage> getMessages(Integer size, boolean withHistory);

    /** Defaults to {@code getMessages(null, true)}. */
    default List<BaseMessage> getMessages() {
        return getMessages(null, true);
    }

    void setMessages(List<BaseMessage> messages, boolean withHistory);

    /** Defaults to {@code setMessages(messages, true)}. */
    default void setMessages(List<BaseMessage> messages) {
        setMessages(messages, true);
    }

    List<BaseMessage> popMessages(int size, boolean withHistory);

    /** Defaults to {@code popMessages(1, true)}. */
    default List<BaseMessage> popMessages() {
        return popMessages(1, true);
    }

    CompletionStage<Void> clearMessages(boolean withHistory);

    /** Defaults to {@code clearMessages(true)}. */
    default CompletionStage<Void> clearMessages() {
        return clearMessages(true);
    }

    CompletionStage<List<BaseMessage>> addMessages(BaseMessage message);

    CompletionStage<List<BaseMessage>> addMessages(List<BaseMessage> messages);

    CompletionStage<ContextWindow> getContextWindow(List<BaseMessage> systemMessages,
                                                    List<ToolInfo> tools,
                                                    Integer windowSize,
                                                    Integer dialogueRound,
                                                    Map<String, Object> kwargs);

    ContextStats statistic();

    String sessionId();

    String contextId();

    TokenCounterPort tokenCounter();

    ToolPort reloaderTool();

    /**
     * Narrow token-counter return type for the abstract context contract.
     *
     * <p>Mirrors Python's {@code TokenCounter} dependency in
     * {@code openjiuwen/core/context_engine/base.py}.</p>
     */
    interface TokenCounterPort {
        int countTokens(List<BaseMessage> messages);
    }

    /**
     * Narrow reload-tool return type for the abstract context contract.
     *
     * <p>Mirrors Python's {@code Tool} dependency in
     * {@code openjiuwen/core/context_engine/base.py}.</p>
     */
    interface ToolPort {
        String name();
    }
}
