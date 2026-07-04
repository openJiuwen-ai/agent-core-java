/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.token;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.List;
import java.util.Map;

/**
 * Backward-compatible token counter base class for the pre-0.1.14 context token package.
 *
 * <p>Mirrors Python's {@code TokenCounter} in
 * {@code openjiuwen/core/context_engine/token/base.py}.</p>
 */
public abstract class TokenCounter implements com.openjiuwen.core.context_engine.token.TokenCounter {
    public abstract int count(String text, String model);

    public int count(String text) {
        return count(text, "");
    }

    public abstract int countMessages(List<BaseMessage> messages, String model);

    public int countMessages(List<BaseMessage> messages) {
        return countMessages(messages, "");
    }

    public abstract int countTools(List<ToolInfo> tools, String model);

    public int countTools(List<ToolInfo> tools) {
        return countTools(tools, "");
    }

    @Override
    public int count(String text, String model, Map<String, Object> kwargs) {
        return count(text, model);
    }

    @Override
    public int countMessages(List<BaseMessage> messages, String model, Map<String, Object> kwargs) {
        return countMessages(messages, model);
    }

    @Override
    public int countTools(List<ToolInfo> tools, String model, Map<String, Object> kwargs) {
        return countTools(tools, model);
    }
}
