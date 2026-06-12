/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the optimizer package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer} in
 * {@code openjiuwen/agent_evolving/optimizer/__init__.py}.</p>
 */
class OptimizerPackageTest {

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertEquals("openjiuwen/agent_evolving/optimizer/__init__.py", OptimizerPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "BaseOptimizer",
                "TextualParameter",
                "LLMCallOptimizerBase",
                "ToolOptimizerBase",
                "MemoryOptimizerBase",
                "InstructionOptimizer",
                "TeamSkillExperienceOptimizer"
        ), OptimizerPackage.EXPORTED_SYMBOLS);
    }
}
