/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

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
 */
public abstract class TokenCounter {

    /**
     * Count the number of tokens in a plain text string.
     *
     * @param text  the text to count tokens for
     * @param model optional model name for model-specific tokenisation
     * @return the token count
     */
    public abstract int count(String text, String model);

    /**
     * Count tokens with default model.
     */
    public int count(String text) {
        return count(text, "");
    }

    /**
     * Count the total tokens across a list of messages.
     *
     * @param messages the messages to count tokens for
     * @param model    optional model name
     * @return the total token count
     */
    public abstract int countMessages(List<BaseMessage> messages, String model);

    /**
     * Count messages tokens with default model.
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
     */
    public abstract int countTools(List<ToolInfo> tools, String model);

    /**
     * Count tool tokens with default model.
     */
    public int countTools(List<ToolInfo> tools) {
        return countTools(tools, "");
    }
}
