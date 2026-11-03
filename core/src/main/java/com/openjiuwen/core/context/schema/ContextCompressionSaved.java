/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code ContextCompressionSaved} in
 * {@code openjiuwen/core/context_engine/schema/context_state.py}.
 */
public class ContextCompressionSaved {
    private int messages;
    private int tokens;
    private double percent;

    public ContextCompressionSaved() {
    }

    public ContextCompressionSaved(int messages, int tokens, double percent) {
        this.messages = messages;
        this.tokens = tokens;
        this.percent = percent;
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

    public double getPercent() {
        return percent;
    }

    public void setPercent(double percent) {
        this.percent = percent;
    }

    public Map<String, Object> modelDump() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messages", messages);
        result.put("tokens", tokens);
        result.put("percent", percent);
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
        if (!(other instanceof ContextCompressionSaved that)) {
            return false;
        }
        return messages == that.messages
                && tokens == that.tokens
                && Double.compare(percent, that.percent) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(messages, tokens, percent);
    }
}
