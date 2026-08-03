/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

/**
 * Camelcase package compatibility facade for handoff routes.
 *
 * <p>Mirrors Python's {@code HandoffRoute} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_config.py}.</p>
 */
public final class HandoffRoute {
    private final String source;
    private final String target;

    public HandoffRoute(String source, String target) {
        this.source = source;
        this.target = target;
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    com.openjiuwen.core.multi_agent.teams.handoff.HandoffRoute toUnderscore() {
        return new com.openjiuwen.core.multi_agent.teams.handoff.HandoffRoute(source, target);
    }
}
