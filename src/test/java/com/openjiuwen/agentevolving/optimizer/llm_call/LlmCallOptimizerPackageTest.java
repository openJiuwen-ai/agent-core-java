/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.llm_call;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the LLM-call optimizer package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.llm_call} in
 * {@code openjiuwen/agent_evolving/optimizer/llm_call/__init__.py}.</p>
 */
class LlmCallOptimizerPackageTest {

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertEquals(
                "openjiuwen/agent_evolving/optimizer/llm_call/__init__.py",
                LlmCallOptimizerPackage.PYTHON_MODULE
        );
        assertEquals(List.of(
                "LLMCallOptimizerBase",
                "InstructionOptimizer"
        ), LlmCallOptimizerPackage.EXPORTED_SYMBOLS);
    }
}
