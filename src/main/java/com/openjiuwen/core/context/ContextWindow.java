/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * A lightweight, serializable snapshot of the messages and tools that will
 * actually be sent to the LLM endpoint.
 * <p>
 * Mirrors Python's {@code ContextWindow} from {@code context_engine/base.py}.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextWindow {
    @Builder.Default
    private List<BaseMessage> systemMessages = new ArrayList<>();

    /**
     * Conversation history or user inputs that may be truncated, compressed,
     * or re-ordered by ContextEngine processors.
     * 
     * @since 0.1.7
     */
    @Builder.Default
    private List<BaseMessage> contextMessages = new ArrayList<>();

    /**
     * Tool definitions (functions, plugins) that the model is allowed to
     * invoke during the turn.
     * 
     * @since 0.1.7
     */
    @Builder.Default
    private List<ToolInfo> tools = new ArrayList<>();

    /**
     * Aggregated statistics for this context window.
     * 
     * @since 0.1.7
     */
    @Builder.Default
    private ContextStats statistic = new ContextStats();

    /**
     * Get all messages (system + context) for sending to the model.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<BaseMessage> getMessages() {
        List<BaseMessage> all = new ArrayList<>(systemMessages.size() + contextMessages.size());
        all.addAll(systemMessages);
        all.addAll(contextMessages);
        return all;
    }

    /**
     * Get the tool definitions.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<ToolInfo> getToolList() {
        return tools;
    }
}
