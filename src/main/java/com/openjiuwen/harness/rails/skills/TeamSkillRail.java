/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.harness.rails.evolution.TeamSkillEvolutionRail;

import java.nio.file.Path;

/**
 * Compatibility shim for the team skill evolution rail.
 *
 * <p>Mirrors Python's {@code TeamSkillRail = TeamSkillEvolutionRail} in
 * {@code openjiuwen/harness/rails/skills/team_skill_rail.py}.</p>
 */
public class TeamSkillRail extends TeamSkillEvolutionRail {
    public TeamSkillRail(Path skillsDir) {
        super(skillsDir);
    }
}
