/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Abstract contract for managing conversational context.
 *
 * <p>Mirrors Python's {@code ModelContext} in
 * {@code openjiuwen/core/context_engine/base.py}.</p>
 */
public interface ModelContext {

    int length();

    List<BaseMessage> getMessages(Integer size, boolean withHistory);

    void setMessages(List<BaseMessage> messages, boolean withHistory);

    List<BaseMessage> popMessages(int size, boolean withHistory);

    CompletionStage<Void> clearMessages(boolean withHistory);

    CompletionStage<List<BaseMessage>> addMessages(BaseMessage message);

    CompletionStage<List<BaseMessage>> addMessages(List<BaseMessage> messages);

    CompletionStage<ContextWindow> getContextWindow(List<BaseMessage> systemMessages,
                                                    List<ToolInfo> tools,
                                                    Integer windowSize,
                                                    Integer dialogueRound,
                                                    Map<String, Object> kwargs);

    ContextStats statistic();

    String sessionId();

    String contextId();

    TokenCounterPort tokenCounter();

    ToolPort reloaderTool();

    /**
     * Narrow token-counter return type for the abstract context contract.
     *
     * <p>Mirrors Python's {@code TokenCounter} dependency in
     * {@code openjiuwen/core/context_engine/base.py}.</p>
     */
    interface TokenCounterPort {
        int countTokens(List<BaseMessage> messages);
    }

    /**
     * Narrow reload-tool return type for the abstract context contract.
     *
     * <p>Mirrors Python's {@code Tool} dependency in
     * {@code openjiuwen/core/context_engine/base.py}.</p>
     */
    interface ToolPort {
        String name();
    }
}
