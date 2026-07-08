/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.List;

/**
 * Camelcase package compatibility facade for handoff configuration.
 *
 * <p>Mirrors Python's {@code HandoffConfig} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/handoff_config.py}.</p>
 */
public class HandoffConfig extends com.openjiuwen.core.multi_agent.teams.handoff.HandoffConfig {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AgentCard startAgent;
        private int maxHandoffs = 10;
        private List<HandoffRoute> routes = new ArrayList<>();

        public Builder startAgent(AgentCard startAgent) {
            this.startAgent = startAgent;
            return this;
        }

        public Builder maxHandoffs(int maxHandoffs) {
            this.maxHandoffs = maxHandoffs;
            return this;
        }

        public Builder routes(List<HandoffRoute> routes) {
            this.routes = routes == null ? new ArrayList<>() : new ArrayList<>(routes);
            return this;
        }

        public HandoffConfig build() {
            HandoffConfig config = new HandoffConfig();
            config.setStartAgent(startAgent);
            config.setMaxHandoffs(maxHandoffs);
            config.setRoutes(routes.stream().map(HandoffRoute::toUnderscore).toList());
            return config;
        }
    }
}
