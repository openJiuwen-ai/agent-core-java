/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.builders.llm_agent} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/__init__.py}.
 */
class LlmAgentPackageTest {

    @Test
    void exportsMirrorPythonAllOrderAndClasses() {
        assertEquals(
                "openjiuwen/dev_tools/agent_builder/builders/llm_agent/__init__.py",
                LlmAgentPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "LlmAgentBuilder",
                "Clarifier",
                "Generator",
                "Transformer"
        ), LlmAgentPackage.ALL);
        assertSame(LlmAgentBuilder.class, LlmAgentPackage.LLM_AGENT_BUILDER);
        assertSame(Clarifier.class, LlmAgentPackage.CLARIFIER);
        assertSame(Generator.class, LlmAgentPackage.GENERATOR);
        assertSame(Transformer.class, LlmAgentPackage.TRANSFORMER);
    }
}
