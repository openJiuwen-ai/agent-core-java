// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.contextengine;

/**
 * Token-usage snapshot for any context container (ModelContext or ContextWindow).
 *
 * 对应 Python: agent-core/openjiuwen/core/context_engine/base.py - ContextStats
 */
public class ContextStats {
    
    /** Sum of system|user|assistant|tool messages. */
    private int totalMessages = 0;
    
    /** Sum of all token fields below. */
    private int totalTokens = 0;
    
    /** Number of system messages. */
    private int systemMessages = 0;
    
    /** Number of user messages. */
    private int userMessages = 0;
    
    /** Number of assistant messages. */
    private int assistantMessages = 0;
    
    /** Number of tool messages. */
    private int toolMessages = 0;
    
    /** Number of ToolInfo objects injected into the prompt. */
    private int tools = 0;
    
    /** Tokens consumed by system messages. */
    private int systemMessageTokens = 0;
    
    /** Tokens consumed by user messages. */
    private int userMessageTokens = 0;
    
    /** Tokens consumed by assistant messages. */
    private int assistantMessageTokens = 0;
    
    /** Tokens consumed by tool messages. */
    private int toolMessageTokens = 0;
    
    /** Tokens consumed by the injected tools (functions, plugins, etc.). */
    private int toolTokens = 0;
    
    public ContextStats() {
    }
    
    // Getters and Setters
    
    public int getTotalMessages() {
        return totalMessages;
    }
    
    public void setTotalMessages(int totalMessages) {
        this.totalMessages = totalMessages;
    }
    
    public int getTotalTokens() {
        return totalTokens;
    }
    
    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }
    
    public int getSystemMessages() {
        return systemMessages;
    }
    
    public void setSystemMessages(int systemMessages) {
        this.systemMessages = systemMessages;
    }
    
    public int getUserMessages() {
        return userMessages;
    }
    
    public void setUserMessages(int userMessages) {
        this.userMessages = userMessages;
    }
    
    public int getAssistantMessages() {
        return assistantMessages;
    }
    
    public void setAssistantMessages(int assistantMessages) {
        this.assistantMessages = assistantMessages;
    }
    
    public int getToolMessages() {
        return toolMessages;
    }
    
    public void setToolMessages(int toolMessages) {
        this.toolMessages = toolMessages;
    }
    
    public int getTools() {
        return tools;
    }
    
    public void setTools(int tools) {
        this.tools = tools;
    }
    
    public int getSystemMessageTokens() {
        return systemMessageTokens;
    }
    
    public void setSystemMessageTokens(int systemMessageTokens) {
        this.systemMessageTokens = systemMessageTokens;
    }
    
    public int getUserMessageTokens() {
        return userMessageTokens;
    }
    
    public void setUserMessageTokens(int userMessageTokens) {
        this.userMessageTokens = userMessageTokens;
    }
    
    public int getAssistantMessageTokens() {
        return assistantMessageTokens;
    }
    
    public void setAssistantMessageTokens(int assistantMessageTokens) {
        this.assistantMessageTokens = assistantMessageTokens;
    }
    
    public int getToolMessageTokens() {
        return toolMessageTokens;
    }
    
    public void setToolMessageTokens(int toolMessageTokens) {
        this.toolMessageTokens = toolMessageTokens;
    }
    
    public int getToolTokens() {
        return toolTokens;
    }
    
    public void setToolTokens(int toolTokens) {
        this.toolTokens = toolTokens;
    }
    
    /**
     * Adds the given token count to the total tokens.
     *
     * @param tokens tokens to add
     */
    public void addTotalTokens(int tokens) {
        this.totalTokens += tokens;
    }
}

