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
 */
public abstract class ModelContext {

    /**
     * Return the length of the context (number of messages).
     */
    public abstract int size();

    /**
     * Retrieve messages from the conversation context without removing them.
     *
     * @param size        number of messages to retrieve; {@code null} for all
     * @param withHistory if true, include history messages
     * @return the retrieved messages in their original order
     */
    public abstract List<BaseMessage> getMessages(Integer size, boolean withHistory);

    /**
     * Get all messages (with history).
     */
    public List<BaseMessage> getMessages() {
        return getMessages((Integer) null, true);
    }

    /**
     * Replace the current message list with the provided one.
     *
     * @param messages    new sequence of messages
     * @param withHistory if true, isReplace both history and context messages
     */
    public abstract void setMessages(List<BaseMessage> messages, boolean withHistory);

    /**
     * Set messages replacing all (with history).
     */
    public void setMessages(List<BaseMessage> messages) {
        setMessages(messages, true);
    }

    /**
     * Remove and return the oldest messages from the context.
     *
     * @param size        number of messages to pop
     * @param withHistory if true, also removes from persistent history
     * @return the messages that were removed
     */
    public abstract List<BaseMessage> popMessages(int size, boolean withHistory);

    /**
     * Pop one message (with history).
     */
    public List<BaseMessage> popMessages() {
        return popMessages(1, true);
    }

    /**
     * Remove all messages added in the current turn.
     *
     * @param withHistory if true, also wipes the persistent history
     */
    public abstract void clearMessages(boolean withHistory);

    /**
     * Add one or more messages to the conversation context.
     * <p>
     * In Java this is synchronous — callers may use Virtual Threads for async.
     *
     * @param messages the messages to add
     * @return the updated message list after insertion
     */
    public abstract List<BaseMessage> addMessages(List<BaseMessage> messages);

    /**
     * Add a single message.
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
     */
    public String compressContext(List<String> processorTypes, Map<String, Object> kwargs) {
        throw new UnsupportedOperationException("compressContext is not implemented");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String compressContext() {
        return compressContext(null, Map.of());
    }

    /**
     * Build and return a window of messages suitable for model inference.
     *
     * @param systemMessages system-level messages to prepend
     * @param tools          tool definitions to include
     * @param windowSize     maximum number of messages; {@code null} for default
     * @param dialogueRound  number of recent rounds to retain; {@code null} disables
     * @param kwargs         additional context-specific parameters (e.g., "model" for KV cache release)
     * @return the constructed context window
     */
    public abstract ContextWindow getContextWindow(
            List<BaseMessage> systemMessages,
            List<ToolInfo> tools,
            Integer windowSize,
            Integer dialogueRound,
            Map<String, Object> kwargs);

    /**
     * Convenience overload without kwargs.
     */
    public ContextWindow getContextWindow(
            List<BaseMessage> systemMessages,
            List<ToolInfo> tools,
            Integer windowSize,
            Integer dialogueRound) {
        return getContextWindow(systemMessages, tools, windowSize, dialogueRound, Map.of());
    }

    /**
     * Get context window with defaults.
     */
    public ContextWindow getContextWindow() {
        return getContextWindow(
                null,
                null,
                (Integer) null,
                (Integer) null,
                Map.of());
    }

    /**
     * Compute context-wide statistics.
     */
    public abstract ContextStats statistic();

    /**
     * Return the session identifier.
     */
    public abstract String sessionId();

    /**
     * Return the context identifier.
     */
    public abstract String contextId();

    /**
     * Return the workspace directory associated with this context, or empty string.
     */
    public String workspaceDir() {
        return "";
    }

    /**
     * Return the sys operation associated with this context, if any.
     */
    public SysOperation sysOperation() {
        return null;
    }

    /**
     * Return the token counter used by this context.
     */
    public abstract TokenCounter tokenCounter();

    /**
     * Return a tool for reloading offloaded messages back into context.
     */
    public abstract Tool reloaderTool();
}
