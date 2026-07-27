/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.skill_call;

import java.util.List;

/**
 * Skill-call optimizer package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.skill_call} in
 * {@code openjiuwen/agent_evolving/optimizer/skill_call/__init__.py}.</p>
 */
public final class SkillCallOptimizerPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/optimizer/skill_call/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "SkillExperienceOptimizer",
            "TeamSkillExperienceOptimizer"
    );

    private SkillCallOptimizerPackage() {
    }
}
