/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rail for team skill orchestration in multi-agent scenarios.
 * <p>
 * Mirrors Python's {@code TeamSkillRail} in
 * {@code openjiuwen.harness.rails.skills.team_skill_rail}.
 */
public class TeamSkillRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(TeamSkillRail.class);

    public TeamSkillRail() {
        super();
    }

    @Override
    public void init(Object agent) {
        LOG.info("[TeamSkillRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[TeamSkillRail] Uninitialized");
    }
}
