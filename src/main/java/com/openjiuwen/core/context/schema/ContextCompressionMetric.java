/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code ContextCompressionMetric} in
 * {@code openjiuwen/core/context_engine/schema/context_state.py}.
 */
public class ContextCompressionMetric {
    private String time;
    private int messages;
    private int tokens;

    @JsonProperty("context_percent")
    private Integer contextPercent;

    public ContextCompressionMetric() {
    }

    public ContextCompressionMetric(String time, int messages, int tokens, Integer contextPercent) {
        this.time = time;
        this.messages = messages;
        this.tokens = tokens;
        this.contextPercent = contextPercent;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public int getMessages() {
        return messages;
    }

    public void setMessages(int messages) {
        this.messages = messages;
    }

    public int getTokens() {
        return tokens;
    }

    public void setTokens(int tokens) {
        this.tokens = tokens;
    }

    public Integer getContextPercent() {
        return contextPercent;
    }

    public void setContextPercent(Integer contextPercent) {
        this.contextPercent = contextPercent;
    }

    public Map<String, Object> modelDump() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("time", time);
        result.put("messages", messages);
        result.put("tokens", tokens);
        result.put("context_percent", contextPercent);
        return result;
    }

    public Map<String, Object> model_dump() {
        return modelDump();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContextCompressionMetric that)) {
            return false;
        }
        return messages == that.messages
                && tokens == that.tokens
                && Objects.equals(time, that.time)
                && Objects.equals(contextPercent, that.contextPercent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, messages, tokens, contextPercent);
    }
}
