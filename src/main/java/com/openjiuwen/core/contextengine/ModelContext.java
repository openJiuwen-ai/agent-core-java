// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.contextengine;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

/**
 * Abstract interface for managing conversational context in a model-agnostic way.
 *
 * Provides a standard interface for adding, retrieving, filtering, and deriving
 * conversation messages, as well as constructing context windows for model inference.
 * Supports both string and BaseMessage content, with configurable placeholder
 * formats for template interpolation.
 *
 * 对应 Python: agent-core/openjiuwen/core/context_engine/base.py - ModelContext
 */
public interface ModelContext {
    
    /**
     * Return the length of the context.
     * The exact unit (number of messages) is implementation-defined.
     *
     * @return the number of messages in the context
     */
    int size();
    
    /**
     * Retrieve messages from the conversation context without removing them.
     *
     * @param size        number of messages to retrieve; null for all
     * @param withHistory if true, return messages with history
     * @return the retrieved messages in their original order
     */
    List<BaseMessage> getMessages(Integer size, boolean withHistory);
    
    /**
     * Retrieve all messages from the conversation context without removing them.
     *
     * @param withHistory if true, return messages with history
     * @return the retrieved messages in their original order
     */
    default List<BaseMessage> getMessages(boolean withHistory) {
        return getMessages(null, withHistory);
    }
    
    /**
     * Retrieve all messages including history.
     *
     * @return the retrieved messages in their original order
     */
    default List<BaseMessage> getMessages() {
        return getMessages(null, true);
    }
    
    /**
     * Replace the current message list with the provided one.
     *
     * @param messages    new sequence of messages to insert into the window
     * @param withHistory if true, replace the concatenated [context_messages + history_messages];
     *                    if false, replace context_messages only, leaving history_messages intact
     */
    void setMessages(List<BaseMessage> messages, boolean withHistory);
    
    /**
     * Replace the current message list with the provided one (including history).
     *
     * @param messages new sequence of messages to insert into the window
     */
    default void setMessages(List<BaseMessage> messages) {
        setMessages(messages, true);
    }
    
    /**
     * Remove and return the oldest messages from the current request's message list.
     *
     * @param size        number of messages to pop (default 1)
     * @param withHistory if true, also removes the corresponding messages from
     *                    the underlying persistent history
     * @return the messages that were removed
     */
    List<BaseMessage> popMessages(int size, boolean withHistory);
    
    /**
     * Remove and return the oldest message from the current request's message list.
     *
     * @param withHistory if true, also removes the corresponding messages from
     *                    the underlying persistent history
     * @return the messages that were removed
     */
    default List<BaseMessage> popMessages(boolean withHistory) {
        return popMessages(1, withHistory);
    }
    
    /**
     * Remove all messages that have been added in the current turn.
     *
     * @param withHistory if true, also wipes the underlying persistent history
     */
    void clearMessages(boolean withHistory);
    
    /**
     * Remove all messages including history.
     */
    default void clearMessages() {
        clearMessages(true);
    }
    
    /**
     * Add one or more messages to the conversation context.
     *
     * @param message a single message to add
     * @return completable future with the updated message list after insertion
     */
    CompletableFuture<List<BaseMessage>> addMessages(BaseMessage message);
    
    /**
     * Add multiple messages to the conversation context.
     *
     * @param messages list of messages to add
     * @return completable future with the updated message list after insertion
     */
    CompletableFuture<List<BaseMessage>> addMessages(List<BaseMessage> messages);
    
    /**
     * Build and return a window of messages suitable for model inference.
     *
     * @param systemMessages system-level messages to prepend to the window
     * @param tools          tool definitions to include in the window
     * @param windowSize     maximum number of historical messages to include; null for all
     * @return completable future with the constructed context window
     */
    CompletableFuture<ContextWindow> getContextWindow(
            List<BaseMessage> systemMessages,
            List<ToolInfo> tools,
            Integer windowSize);
    
    /**
     * Build and return a window of messages suitable for model inference with default settings.
     *
     * @return completable future with the constructed context window
     */
    default CompletableFuture<ContextWindow> getContextWindow() {
        return getContextWindow(null, null, null);
    }
    
    /**
     * Compute context-wide statistics.
     *
     * @return aggregated message and token counts for the context
     */
    ContextStats statistic();
    
    /**
     * Return the globally unique identifier of the current user session.
     *
     * @return session ID
     */
    String getSessionId();
    
    /**
     * Return the globally unique identifier of the current context
     * (conversation, request, or task) within the session.
     *
     * @return context ID
     */
    String getContextId();
}

