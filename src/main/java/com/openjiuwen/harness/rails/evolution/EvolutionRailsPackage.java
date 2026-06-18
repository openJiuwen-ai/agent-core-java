/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import java.util.List;

/**
 * Module facade for evolution rails.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/rails/evolution/__init__.py}.</p>
 */
public final class EvolutionRailsPackage {

    private EvolutionRailsPackage() {
    }

    public static List<Class<?>> exports() {
        return List.of(
                ContextEvolutionRail.class,
                EvolutionRail.class,
                EvolutionTriggerPoint.class,
                SkillEvolutionRail.class,
                SkillEvolutionSharing.class,
                TeamSkillEvolutionRail.class,
                TrajectoryRail.class
        );
    }
}
