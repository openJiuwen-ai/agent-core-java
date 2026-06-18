/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.harness.rails.CallbackContext;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Team-skill evolution rail.
 *
 * <p>Mirrors Python's {@code TeamSkillEvolutionRail} in
 * {@code openjiuwen/harness/rails/evolution/team_skill_evolution_rail.py}.</p>
 */
public class TeamSkillEvolutionRail extends SkillEvolutionRail {

    public TeamSkillEvolutionRail(Path skillsDir) {
        super(skillsDir);
    }

    @Override
    protected Map<String, Object> snapshotForEvolution(CallbackContext ctx) {
        Map<String, Object> snapshot = new LinkedHashMap<>(super.snapshotForEvolution(ctx));
        snapshot.put("team_skill", true);
        return snapshot;
    }
}
