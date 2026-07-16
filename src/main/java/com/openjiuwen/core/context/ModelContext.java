/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context;

import com.openjiuwen.core.context.token.TokenCounter;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.sysop.SysOperation;

import java.util.List;
import java.util.Map;

/**
 * Abstract base class for managing conversational context in a model-agnostic way.
 * <p>
 * Provides a standard interface for adding, retrieving, filtering, and deriving
 * conversation messages, as well as constructing context windows for model inference.
 * <p>
 * Mirrors Python's {@code ModelContext} ABC from {@code context_engine/base.py}.
 * <p>
 * Note: Python async methods are mapped to synchronous Java methods. Callers may
 * run them on Virtual Threads for concurrency.
 * 
 * @since 0.1.7
 */
public abstract class ModelContext {
    /**
     * size.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract int size();

    /**
     * Retrieve messages from the conversation context without removing them.
     * 
     * @param size number of messages to retrieve; {@code null} for all
     * @param withHistory if true, include history messages
     * @return the retrieved messages in their original order
     * @since 0.1.7
     */
    public abstract List<BaseMessage> getMessages(Integer size, boolean withHistory);

    /**
     * Get all messages (with history).
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<BaseMessage> getMessages() {
        return getMessages((Integer) null, true);
    }

    /**
     * Replace the current message list with the provided one.
     * 
     * @param messages new sequence of messages
     * @param withHistory if true, isReplace both history and context messages
     * @since 0.1.7
     */
    public abstract void setMessages(List<BaseMessage> messages, boolean withHistory);

    /**
     * Set messages replacing all (with history).
     * 
     * @param messages messages
     * @since 0.1.7
     */
    public void setMessages(List<BaseMessage> messages) {
        setMessages(messages, true);
    }

    /**
     * Remove and return the oldest messages from the context.
     * 
     * @param size number of messages to pop
     * @param withHistory if true, also removes from persistent history
     * @return the messages that were removed
     * @since 0.1.7
     */
    public abstract List<BaseMessage> popMessages(int size, boolean withHistory);

    /**
     * Pop one message (with history).
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<BaseMessage> popMessages() {
        return popMessages(1, true);
    }

    /**
     * Remove all messages added in the current turn.
     * 
     * @param withHistory if true, also wipes the persistent history
     * @since 0.1.7
     */
    public abstract void clearMessages(boolean withHistory);

    /**
     * Add one or more messages to the conversation context.
     * <p>
     * In Java this is synchronous — callers may use Virtual Threads for async.
     * 
     * @param messages the messages to add
     * @return the updated message list after insertion
     * @since 0.1.7
     */
    public abstract List<BaseMessage> addMessages(List<BaseMessage> messages);

    /**
     * Add a single message.
     * 
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    public List<BaseMessage> addMessages(BaseMessage message) {
        return addMessages(List.of(message));
    }

    /**
     * Actively compress the context using registered compression processors.
     * 
     * @param processorTypes optional target processor type names
     * @param kwargs additional context-specific parameters
     * @return compression result marker such as {@code compressed}, {@code noop}, or {@code busy}
     * @since 0.1.7
     */
    public String compressContext(List<String> processorTypes, Map<String, Object> kwargs) {
        throw new UnsupportedOperationException("compressContext is not implemented");
    }

    /**
     * compressContext.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String compressContext() {
        return compressContext(null, Map.of());
    }

    /**
     * Build and return a window of messages suitable for model inference.
     * 
     * @param systemMessages system-level messages to prepend
     * @param tools tool definitions to include
     * @param windowSize maximum number of messages; {@code null} for default
     * @param dialogueRound number of recent rounds to retain; {@code null} disables
     * @param kwargs additional context-specific parameters (e.g., "model" for KV cache release)
     * @return the constructed context window
     * @since 0.1.7
     */
    public abstract ContextWindow getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools,
            Integer windowSize, Integer dialogueRound, Map<String, Object> kwargs);

    /**
     * Convenience overload without kwargs.
     * 
     * @param systemMessages systemMessages
     * @param tools tools
     * @param windowSize windowSize
     * @param dialogueRound dialogueRound
     * @return the result
     * @since 0.1.7
     */
    public ContextWindow getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools, Integer windowSize,
            Integer dialogueRound) {
        return getContextWindow(systemMessages, tools, windowSize, dialogueRound, Map.of());
    }

    /**
     * Get context window with defaults.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ContextWindow getContextWindow() {
        return getContextWindow(null, null, (Integer) null, (Integer) null, Map.of());
    }

    /**
     * Compute context-wide statistics.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract ContextStats statistic();

    /**
     * Return the session identifier.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract String sessionId();

    /**
     * Return the context identifier.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract String contextId();

    /**
     * Return the workspace directory associated with this context, or empty string.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String workspaceDir() {
        return "";
    }

    /**
     * Return the sys operation associated with this context, if any.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SysOperation sysOperation() {
        return null;
    }

    /**
     * Return the token counter used by this context.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract TokenCounter tokenCounter();

    /**
     * Return a tool for reloading offloaded messages back into context.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract Tool reloaderTool();
}
