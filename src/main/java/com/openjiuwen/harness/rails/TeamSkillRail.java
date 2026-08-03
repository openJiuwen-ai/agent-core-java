/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.llm.Model;
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

    public TeamSkillRail(String skillsDir) {
        this(Path.of(skillsDir));
    }

    public TeamSkillRail(Path skillsDir, Model llm, String model, boolean autoSave, boolean asyncEvolution) {
        super(skillsDir, llm, model, autoSave, asyncEvolution);
    }

    public TeamSkillRail(String skillsDir, Model llm, String model, boolean autoSave, boolean asyncEvolution) {
        this(Path.of(skillsDir), llm, model, autoSave, asyncEvolution);
    }
}
