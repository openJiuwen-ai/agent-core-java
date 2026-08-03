/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine;

import java.util.Objects;

/**
 * Token-usage snapshot for a context container.
 *
 * <p>Mirrors Python's {@code ContextStats} in
 * {@code openjiuwen/core/context_engine/base.py}.</p>
 */
public class ContextStats {

    private int totalMessages;
    private int totalTokens;
    private int totalDialogues;
    private int systemMessages;
    private int userMessages;
    private int assistantMessages;
    private int toolMessages;
    private int tools;
    private int systemMessageTokens;
    private int userMessageTokens;
    private int assistantMessageTokens;
    private int toolMessageTokens;
    private int toolTokens;

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

    public int getTotalDialogues() {
        return totalDialogues;
    }

    public void setTotalDialogues(int totalDialogues) {
        this.totalDialogues = totalDialogues;
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

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ContextStats that)) {
            return false;
        }
        return totalMessages == that.totalMessages
                && totalTokens == that.totalTokens
                && totalDialogues == that.totalDialogues
                && systemMessages == that.systemMessages
                && userMessages == that.userMessages
                && assistantMessages == that.assistantMessages
                && toolMessages == that.toolMessages
                && tools == that.tools
                && systemMessageTokens == that.systemMessageTokens
                && userMessageTokens == that.userMessageTokens
                && assistantMessageTokens == that.assistantMessageTokens
                && toolMessageTokens == that.toolMessageTokens
                && toolTokens == that.toolTokens;
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalMessages, totalTokens, totalDialogues, systemMessages, userMessages,
                assistantMessages, toolMessages, tools, systemMessageTokens, userMessageTokens,
                assistantMessageTokens, toolMessageTokens, toolTokens);
    }
}
