/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Serializable snapshot of messages and tools sent to a model.
 *
 * <p>Mirrors Python's {@code ContextWindow} in
 * {@code openjiuwen/core/context_engine/base.py}.</p>
 */
public class ContextWindow {

    private List<BaseMessage> systemMessages = new ArrayList<>();
    private List<BaseMessage> contextMessages = new ArrayList<>();
    private List<ToolInfo> tools = new ArrayList<>();
    private ContextStats statistic = new ContextStats();

    public ContextWindow() {
    }

    public ContextWindow(List<BaseMessage> systemMessages, List<BaseMessage> contextMessages, List<ToolInfo> tools,
                         ContextStats statistic) {
        setSystemMessages(systemMessages);
        setContextMessages(contextMessages);
        setTools(tools);
        setStatistic(statistic);
    }

    public List<BaseMessage> getSystemMessages() {
        return new ArrayList<>(systemMessages);
    }

    public void setSystemMessages(List<BaseMessage> systemMessages) {
        this.systemMessages = systemMessages == null ? new ArrayList<>() : new ArrayList<>(systemMessages);
    }

    public List<BaseMessage> getContextMessages() {
        return new ArrayList<>(contextMessages);
    }

    public void setContextMessages(List<BaseMessage> contextMessages) {
        this.contextMessages = contextMessages == null ? new ArrayList<>() : new ArrayList<>(contextMessages);
    }

    public List<ToolInfo> getTools() {
        return new ArrayList<>(tools);
    }

    public void setTools(List<ToolInfo> tools) {
        this.tools = tools == null ? new ArrayList<>() : new ArrayList<>(tools);
    }

    public ContextStats getStatistic() {
        return statistic;
    }

    public void setStatistic(ContextStats statistic) {
        this.statistic = statistic == null ? new ContextStats() : statistic;
    }

    public List<BaseMessage> getMessages() {
        List<BaseMessage> messages = new ArrayList<>(systemMessages);
        messages.addAll(contextMessages);
        return messages;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ContextWindow that)) {
            return false;
        }
        return Objects.equals(systemMessages, that.systemMessages)
                && Objects.equals(contextMessages, that.contextMessages)
                && Objects.equals(tools, that.tools)
                && Objects.equals(statistic, that.statistic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(systemMessages, contextMessages, tools, statistic);
    }
}
