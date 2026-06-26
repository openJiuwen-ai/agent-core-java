/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.token;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code TokenCounter} in
 * {@code openjiuwen/core/context_engine/token/base.py}.
 */
public interface TokenCounter {
    int count(String text, String model, Map<String, Object> kwargs);

    default int count(String text, String model) {
        return count(text, model, Map.of());
    }

    default int count(String text) {
        return count(text, "", Map.of());
    }

    int countMessages(List<BaseMessage> messages, String model, Map<String, Object> kwargs);

    default int countMessages(List<BaseMessage> messages, String model) {
        return countMessages(messages, model, Map.of());
    }

    default int countMessages(List<BaseMessage> messages) {
        return countMessages(messages, "", Map.of());
    }

    int countTools(List<ToolInfo> tools, String model, Map<String, Object> kwargs);

    default int countTools(List<ToolInfo> tools, String model) {
        return countTools(tools, model, Map.of());
    }

    default int countTools(List<ToolInfo> tools) {
        return countTools(tools, "", Map.of());
    }
}
