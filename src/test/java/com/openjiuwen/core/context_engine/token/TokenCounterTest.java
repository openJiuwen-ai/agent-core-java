/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.token;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies translated token counter contract behavior.
 *
 * <p>Mirrors Python's {@code TokenCounter} in
 * {@code openjiuwen/core/context_engine/token/base.py}.</p>
 */
class TokenCounterTest {
    @Test
    void defaultOverloadsMirrorPythonKeywordDefaults() {
        RecordingTokenCounter counter = new RecordingTokenCounter();

        assertEquals(4, counter.count("abcd"));
        assertEquals(7, counter.count("abcd", "gpt"));
        assertEquals(8, counter.count("abcd", "gpt", Map.of("bias", 1)));

        assertEquals(1, counter.countMessages(List.of(new BaseMessage("user", "hello"))));
        assertEquals(2, counter.countMessages(List.of(new BaseMessage("user", "hello")), "m"));

        ToolInfo toolInfo = new ToolInfo();
        toolInfo.setName("search");
        assertEquals(1, counter.countTools(List.of(toolInfo)));
        assertEquals(2, counter.countTools(List.of(toolInfo), "m"));
        assertTrue(counter.sawEmptyKwargs);
    }

    /**
     * Mirrors Python's concrete {@code TokenCounter} subclass contract in
     * {@code openjiuwen/core/context_engine/token/base.py}.
     */
    private static final class RecordingTokenCounter implements TokenCounter {
        private boolean sawEmptyKwargs;

        @Override
        public int count(String text, String model, Map<String, Object> kwargs) {
            sawEmptyKwargs = sawEmptyKwargs || kwargs.isEmpty();
            return text.length() + model.length() + kwargs.size();
        }

        @Override
        public int countMessages(List<BaseMessage> messages, String model, Map<String, Object> kwargs) {
            sawEmptyKwargs = sawEmptyKwargs || kwargs.isEmpty();
            return messages.size() + model.length() + kwargs.size();
        }

        @Override
        public int countTools(List<ToolInfo> tools, String model, Map<String, Object> kwargs) {
            sawEmptyKwargs = sawEmptyKwargs || kwargs.isEmpty();
            return tools.size() + model.length() + kwargs.size();
        }
    }
}
