/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.skill_call;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's package surface in
 * {@code openjiuwen/core/operator/skill_call/__init__.py}.
 */
class SkillCallPackageTest {

    @Test
    void exposesPythonPackageBridge() {
        assertEquals(
                "openjiuwen/core/operator/skill_call/__init__.py",
                SkillCallPackage.PYTHON_MODULE
        );
        assertEquals("Skill experience operator package.", SkillCallPackage.DESCRIPTION);
        assertEquals(
                List.of("SkillExperienceOperator", "SkillCallOperator"),
                SkillCallPackage.EXPORTED_SYMBOLS
        );
        assertSame(SkillExperienceOperator.class, SkillCallPackage.SKILL_EXPERIENCE_OPERATOR);
        assertSame(SkillCallOperator.class, SkillCallPackage.SKILL_CALL_OPERATOR);
    }
}
