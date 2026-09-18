/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.skill_call;

import java.util.function.BiConsumer;

/**
 * Mirrors Python's {@code SkillCallOperator} alias in
 * {@code openjiuwen/core/operator/skill_call/base.py}.
 */
public final class SkillCallOperator extends SkillExperienceOperator {

    public SkillCallOperator(String skillName) {
        super(skillName);
    }

    public SkillCallOperator(String skillName, BiConsumer<String, Object> onParameterUpdated) {
        super(skillName, onParameterUpdated);
    }
}
