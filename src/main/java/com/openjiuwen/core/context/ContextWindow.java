/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Backward-compatible context window for the pre-0.1.14 root context package.
 *
 * <p>Mirrors Python's {@code ContextWindow} in
 * {@code openjiuwen/core/context_engine/base.py}.</p>
 */
public class ContextWindow {
    private List<BaseMessage> systemMessages = new ArrayList<>();
    private List<BaseMessage> contextMessages = new ArrayList<>();
    private List<ToolInfo> tools = new ArrayList<>();
    private com.openjiuwen.core.context_engine.ContextStats statistic =
            new com.openjiuwen.core.context_engine.ContextStats();

    public ContextWindow() {
    }

    public ContextWindow(List<BaseMessage> systemMessages, List<BaseMessage> contextMessages, List<ToolInfo> tools,
                         com.openjiuwen.core.context_engine.ContextStats statistic) {
        setSystemMessages(systemMessages);
        setContextMessages(contextMessages);
        setTools(tools);
        setStatistic(statistic);
    }

    public static ContextWindow from(com.openjiuwen.core.context_engine.ContextWindow source) {
        if (source == null) {
            return null;
        }
        return new ContextWindow(source.getSystemMessages(), source.getContextMessages(), source.getTools(),
                source.getStatistic());
    }

    public com.openjiuwen.core.context_engine.ContextWindow toContextEngineWindow() {
        return new com.openjiuwen.core.context_engine.ContextWindow(systemMessages, contextMessages, tools, statistic);
    }

    public static Builder builder() {
        return new Builder();
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

    public com.openjiuwen.core.context_engine.ContextStats getStatistic() {
        return statistic;
    }

    public void setStatistic(com.openjiuwen.core.context_engine.ContextStats statistic) {
        this.statistic = statistic == null ? new com.openjiuwen.core.context_engine.ContextStats() : statistic;
    }

    public List<BaseMessage> getMessages() {
        List<BaseMessage> messages = new ArrayList<>(systemMessages.size() + contextMessages.size());
        messages.addAll(systemMessages);
        messages.addAll(contextMessages);
        return messages;
    }

    public List<ToolInfo> getToolList() {
        return getTools();
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

    public static final class Builder {
        private List<BaseMessage> systemMessages;
        private List<BaseMessage> contextMessages;
        private List<ToolInfo> tools;
        private com.openjiuwen.core.context_engine.ContextStats statistic;

        private Builder() {
        }

        public Builder systemMessages(List<BaseMessage> systemMessages) {
            this.systemMessages = systemMessages;
            return this;
        }

        public Builder contextMessages(List<BaseMessage> contextMessages) {
            this.contextMessages = contextMessages;
            return this;
        }

        public Builder tools(List<ToolInfo> tools) {
            this.tools = tools;
            return this;
        }

        public Builder statistic(com.openjiuwen.core.context_engine.ContextStats statistic) {
            this.statistic = statistic;
            return this;
        }

        public ContextWindow build() {
            return new ContextWindow(systemMessages, contextMessages, tools, statistic);
        }
    }
}
