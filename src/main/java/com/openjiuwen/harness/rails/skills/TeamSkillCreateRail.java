/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rail for team skill creation in multi-agent scenarios.
 * <p>
 * Mirrors Python's {@code TeamSkillCreateRail} in
 * {@code openjiuwen.harness.rails.skills.team_skill_create_rail}.
 */
public class TeamSkillCreateRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(TeamSkillCreateRail.class);

    public TeamSkillCreateRail() {
        super();
    }

    @Override
    public void init(Object agent) {
        LOG.info("[TeamSkillCreateRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[TeamSkillCreateRail] Uninitialized");
    }
}
