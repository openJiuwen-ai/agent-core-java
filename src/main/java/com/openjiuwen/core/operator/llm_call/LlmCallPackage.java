/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.operator.llm_call;

import java.util.List;

/**
 * Package bridge for LLM-call operator exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.operator.llm_call} in
 * {@code openjiuwen/core/operator/llm_call/__init__.py}.</p>
 */
public final class LlmCallPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/operator/llm_call/__init__.py";
    public static final String DESCRIPTION = "LLM invocation operators: LLMCallOperator with prompt tunables.";
    public static final Class<LLMCallOperator> LLM_CALL_OPERATOR = LLMCallOperator.class;
    public static final Class<LLMCall> LLM_CALL = LLMCall.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "LLMCallOperator",
            "LLMCall"
    );

    private LlmCallPackage() {
    }
}
