/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer;

import java.util.List;

/**
 * Public optimizer package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer} in
 * {@code openjiuwen/agent_evolving/optimizer/__init__.py}.</p>
 */
public final class OptimizerPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/optimizer/__init__.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "BaseOptimizer",
            "TextualParameter",
            "LLMCallOptimizerBase",
            "ToolOptimizerBase",
            "MemoryOptimizerBase",
            "InstructionOptimizer",
            "TeamSkillExperienceOptimizer"
    );

    private OptimizerPackage() {
    }
}
