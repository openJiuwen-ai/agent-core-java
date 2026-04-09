/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.llm_call;

import com.openjiuwen.core.foundation.llm.Model;

import java.util.function.BiConsumer;

/**
 * Backward compatible alias of {@link LLMCallOperator}.
 */
public class LLMCall extends LLMCallOperator {

    public LLMCall(String modelName,
                   Model llm,
                   Object systemPrompt,
                   Object userPrompt,
                   boolean freezeSystemPrompt,
                   boolean freezeUserPrompt,
                   String llmCallId,
                   BiConsumer<String, Object> onParameterUpdated) {
        super(modelName, llm, systemPrompt, userPrompt, freezeSystemPrompt, freezeUserPrompt, llmCallId,
                onParameterUpdated);
    }

    public LLMCall(String modelName, Model llm, Object systemPrompt, Object userPrompt) {
        super(modelName, llm, systemPrompt, userPrompt);
    }
}
