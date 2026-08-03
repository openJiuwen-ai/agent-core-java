/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.evolution.TeamSkillEvolutionRail;

import java.util.List;

/**
 * Module facade for skill rails.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/rails/skills/__init__.py}.</p>
 */
public final class SkillsRailsPackage {

    private SkillsRailsPackage() {
    }

    public static List<Class<? extends DeepAgentRail>> exportedRails() {
        return List.of(
                SkillUseRail.class,
                SkillCreateRail.class,
                TeamSkillCreateRail.class,
                TeamSkillEvolutionRail.class,
                TeamSkillRail.class
        );
    }
}
