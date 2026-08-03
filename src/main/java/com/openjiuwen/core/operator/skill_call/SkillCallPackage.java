/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.skill_call;

import java.util.List;

/**
 * Package bridge for skill-call operator exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/core/operator/skill_call/__init__.py}.
 * </p>
 */
public final class SkillCallPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/operator/skill_call/__init__.py";
    public static final String DESCRIPTION = "Skill experience operator package.";
    public static final Class<SkillExperienceOperator> SKILL_EXPERIENCE_OPERATOR = SkillExperienceOperator.class;
    public static final Class<SkillCallOperator> SKILL_CALL_OPERATOR = SkillCallOperator.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "SkillExperienceOperator",
            "SkillCallOperator"
    );

    private SkillCallPackage() {
    }
}
