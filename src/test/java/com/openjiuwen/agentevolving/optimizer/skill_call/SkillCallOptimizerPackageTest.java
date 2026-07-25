/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for skill-call optimizer package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.skill_call} in
 * {@code openjiuwen/agent_evolving/optimizer/skill_call/__init__.py}.</p>
 */
class SkillCallOptimizerPackageTest {

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertEquals(
                "openjiuwen/agent_evolving/optimizer/skill_call/__init__.py",
                SkillCallOptimizerPackage.PYTHON_MODULE
        );
        assertEquals(List.of(
                "SkillExperienceOptimizer",
                "TeamSkillExperienceOptimizer"
        ), SkillCallOptimizerPackage.EXPORTED_SYMBOLS);
    }
}
