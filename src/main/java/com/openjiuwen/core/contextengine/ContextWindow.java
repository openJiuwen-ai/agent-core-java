// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.contextengine;

import java.util.ArrayList;
import java.util.List;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

/**
 * A lightweight, serializable snapshot of the messages and tools that will
 * actually be sent to the LLM endpoint.
 *
 * 对应 Python: agent-core/openjiuwen/core/context_engine/base.py - ContextWindow
 */
public class ContextWindow {
    
    /**
     * System-level directives (e.g., instructions, personas) that should
     * remain at the beginning of the final message list.
     */
    private List<BaseMessage> systemMessages;
    
    /**
     * Conversation history or user inputs that may be truncated, compressed,
     * or re-ordered by ContextEngine processors.
     */
    private List<BaseMessage> contextMessages;
    
    /**
     * Tool definitions (functions, plugins) that the model is allowed to
     * invoke during the turn.
     */
    private List<ToolInfo> tools;
    
    /**
     * Statistics for this context window.
     */
    private ContextStats statistic;
    
    public ContextWindow() {
        this.systemMessages = new ArrayList<>();
        this.contextMessages = new ArrayList<>();
        this.tools = new ArrayList<>();
        this.statistic = new ContextStats();
    }
    
    public ContextWindow(List<BaseMessage> systemMessages, 
                        List<BaseMessage> contextMessages, 
                        List<ToolInfo> tools) {
        this.systemMessages = systemMessages != null ? new ArrayList<>(systemMessages) : new ArrayList<>();
        this.contextMessages = contextMessages != null ? new ArrayList<>(contextMessages) : new ArrayList<>();
        this.tools = tools != null ? new ArrayList<>(tools) : new ArrayList<>();
        this.statistic = new ContextStats();
    }
    
    /**
     * Returns the combined list of system messages and context messages.
     *
     * @return combined message list
     */
    public List<BaseMessage> getMessages() {
        List<BaseMessage> messages = new ArrayList<>();
        messages.addAll(systemMessages);
        messages.addAll(contextMessages);
        return messages;
    }
    
    /**
     * Returns the list of tools.
     *
     * @return tool list
     */
    public List<ToolInfo> getTools() {
        return tools;
    }
    
    // Getters and Setters
    
    public List<BaseMessage> getSystemMessages() {
        return systemMessages;
    }
    
    public void setSystemMessages(List<BaseMessage> systemMessages) {
        this.systemMessages = systemMessages != null ? systemMessages : new ArrayList<>();
    }
    
    public List<BaseMessage> getContextMessages() {
        return contextMessages;
    }
    
    public void setContextMessages(List<BaseMessage> contextMessages) {
        this.contextMessages = contextMessages != null ? contextMessages : new ArrayList<>();
    }
    
    public void setTools(List<ToolInfo> tools) {
        this.tools = tools != null ? tools : new ArrayList<>();
    }
    
    public ContextStats getStatistic() {
        return statistic;
    }
    
    public void setStatistic(ContextStats statistic) {
        this.statistic = statistic != null ? statistic : new ContextStats();
    }
}

