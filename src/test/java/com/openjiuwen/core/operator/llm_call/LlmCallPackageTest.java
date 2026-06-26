/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.operator.llm_call;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for the LLM-call package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.operator.llm_call} in
 * {@code openjiuwen/core/operator/llm_call/__init__.py}.</p>
 */
class LlmCallPackageTest {

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertEquals("openjiuwen/core/operator/llm_call/__init__.py", LlmCallPackage.PYTHON_MODULE);
        assertEquals(List.of("LLMCallOperator", "LLMCall"), LlmCallPackage.EXPORTED_SYMBOLS);
        assertSame(LLMCallOperator.class, LlmCallPackage.LLM_CALL_OPERATOR);
        assertSame(LLMCall.class, LlmCallPackage.LLM_CALL);
    }
}
