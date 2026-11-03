/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.operator.llm_call;

import java.util.function.BiConsumer;

/**
 * Backward compatible LLM-call operator alias.
 *
 * <p>Mirrors Python's {@code LLMCall} alias in
 * {@code openjiuwen/core/operator/llm_call/base.py}.</p>
 */
public class LLMCall extends LLMCallOperator {

    public LLMCall(Object systemPrompt, Object userPrompt) {
        super(systemPrompt, userPrompt);
    }

    public LLMCall(Object systemPrompt,
                   Object userPrompt,
                   boolean freezeSystemPrompt,
                   boolean freezeUserPrompt,
                   String operatorId,
                   BiConsumer<String, Object> onParameterUpdated) {
        super(systemPrompt, userPrompt, freezeSystemPrompt, freezeUserPrompt, operatorId, onParameterUpdated);
    }
}
