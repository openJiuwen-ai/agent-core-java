/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.multi_agent.TeamConfig;

/**
 * Full configuration for a handoff team.
 *
 * <p>Mirrors Python's {@code HandoffTeamConfig} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class HandoffTeamConfig extends TeamConfig {

    @JsonProperty("handoff")
    private HandoffConfig handoff = new HandoffConfig();

    public HandoffTeamConfig() {
        super();
    }

    public HandoffTeamConfig(HandoffConfig handoff) {
        this.handoff = handoff == null ? new HandoffConfig() : handoff;
    }

    public HandoffConfig getHandoff() {
        return handoff;
    }

    public void setHandoff(HandoffConfig handoff) {
        this.handoff = handoff == null ? new HandoffConfig() : handoff;
    }
}
