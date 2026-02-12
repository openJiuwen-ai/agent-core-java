// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.contextengine.token;

import java.util.List;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

/**
 * Abstract interface for unified token counting.
 * A concrete implementation only needs to override count;
 * countMessages and countTools can be reused or overridden as required.
 *
 * 对应 Python: agent-core/openjiuwen/core/context_engine/token/base.py - TokenCounter
 */
public interface TokenCounter {
    
    /**
     * Count tokens in a single text.
     *
     * @param text  the input text to tokenize
     * @param model the model name that determines the tokenization rule
     * @return the number of tokens in text
     */
    int count(String text, String model);
    
    /**
     * Count tokens in a single text with default model.
     *
     * @param text the input text to tokenize
     * @return the number of tokens in text
     */
    default int count(String text) {
        return count(text, "");
    }
    
    /**
     * Count tokens for a list of chat messages.
     * The default convention is OpenAI-style: &lt;|start|&gt;{role}\n{content}&lt;|end|&gt;.
     *
     * @param messages list of message objects (with role/content)
     * @param model    the model name that determines the tokenization rule
     * @return the total estimated token count for messages
     */
    int countMessages(List<BaseMessage> messages, String model);
    
    /**
     * Count tokens for a list of chat messages with default model.
     *
     * @param messages list of message objects (with role/content)
     * @return the total estimated token count for messages
     */
    default int countMessages(List<BaseMessage> messages) {
        return countMessages(messages, "");
    }
    
    /**
     * Count the number of tokens that a list of tool-calling metadata will consume.
     *
     * @param tools list of ToolInfo objects describing the tools to be injected into the prompt
     * @param model the target model name, which determines the tokenization rule
     * @return total tokens required to represent the tools in the prompt
     */
    int countTools(List<ToolInfo> tools, String model);
    
    /**
     * Count the number of tokens that a list of tool-calling metadata will consume with default model.
     *
     * @param tools list of ToolInfo objects describing the tools to be injected into the prompt
     * @return total tokens required to represent the tools in the prompt
     */
    default int countTools(List<ToolInfo> tools) {
        return countTools(tools, "");
    }
}

