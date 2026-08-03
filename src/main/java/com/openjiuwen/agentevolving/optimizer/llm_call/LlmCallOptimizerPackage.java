/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.llm_call;

import java.util.List;

/**
 * Public LLM-call optimizer package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.llm_call} in
 * {@code openjiuwen/agent_evolving/optimizer/llm_call/__init__.py}.</p>
 */
public final class LlmCallOptimizerPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/optimizer/llm_call/__init__.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "LLMCallOptimizerBase",
            "InstructionOptimizer"
    );

    private LlmCallOptimizerPackage() {
    }
}
