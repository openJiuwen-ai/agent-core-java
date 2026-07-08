/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

/**
 * Camelcase package compatibility facade for handoff team configuration.
 *
 * <p>Mirrors Python's {@code HandoffTeamConfig} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_config.py}.</p>
 */
public class HandoffTeamConfig extends com.openjiuwen.core.multi_agent.teams.handoff.HandoffTeamConfig {

    public HandoffTeamConfig() {
        super();
    }

    public HandoffTeamConfig(HandoffConfig handoff) {
        super(handoff);
    }
}
