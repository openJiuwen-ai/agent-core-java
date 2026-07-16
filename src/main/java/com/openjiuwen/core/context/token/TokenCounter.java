/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.token;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.List;

/**
 * Abstract base class for token counting.
 * <p>
 * Provides a unified interface for counting tokens in text, messages, and
 * tool definitions. Concrete implementations should provide model-specific
 * tokenisation logic.
 * <p>
 * Mirrors Python's {@code TokenCounter} ABC from {@code context_engine/token/base.py}.
 * 
 * @since 0.1.7
 */
public abstract class TokenCounter {
    /**
     * count.
     * 
     * @param text text
     * @param model model
     * @return the result
     * @since 0.1.7
     */
    public abstract int count(String text, String model);

    /**
     * Count tokens with default model.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    public int count(String text) {
        return count(text, "");
    }

    /**
     * Count the total tokens across a list of messages.
     * 
     * @param messages the messages to count tokens for
     * @param model optional model name
     * @return the total token count
     * @since 0.1.7
     */
    public abstract int countMessages(List<BaseMessage> messages, String model);

    /**
     * Count messages tokens with default model.
     * 
     * @param messages messages
     * @return the result
     * @since 0.1.7
     */
    public int countMessages(List<BaseMessage> messages) {
        return countMessages(messages, "");
    }

    /**
     * Count the total tokens across a list of tool definitions.
     * 
     * @param tools the tool definitions to count tokens for
     * @param model optional model name
     * @return the total token count
     * @since 0.1.7
     */
    public abstract int countTools(List<ToolInfo> tools, String model);

    /**
     * Count tool tokens with default model.
     * 
     * @param tools tools
     * @return the result
     * @since 0.1.7
     */
    public int countTools(List<ToolInfo> tools) {
        return countTools(tools, "");
    }
}
