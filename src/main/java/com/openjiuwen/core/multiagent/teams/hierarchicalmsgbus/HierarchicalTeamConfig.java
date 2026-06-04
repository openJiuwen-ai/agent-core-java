/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchicalmsgbus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.openjiuwen.core.multiagent.config.TeamConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Optional;

/**
 * Configuration for HierarchicalTeam (messagebus mode).
 * <p>
 * Mirrors Python's {@code HierarchicalTeamConfig} in 
 * {@code openjiuwen.core.multi_agent.teams.hierarchical_msgbus.hierarchical_config}.
 * <p>
 * Extends TeamConfig with supervisor agent and timeout settings.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HierarchicalTeamConfig extends TeamConfig {
    
    /** Top-level entry supervisor AgentCard (required). */
    private AgentCard supervisorAgent;
    
    /** Timeout in seconds for P2P message communication. */
    private Double timeout = 1800.0;

    public HierarchicalTeamConfig(AgentCard supervisorAgent) {
        super();
        this.supervisorAgent = supervisorAgent;
    }

    public HierarchicalTeamConfig(AgentCard supervisorAgent, Double timeout) {
        this(supervisorAgent);
        this.timeout = timeout;
    }
    
    public Optional<Double> getTimeout() {
        return Optional.ofNullable(timeout);
    }

    public double getP2pTimeout() {
        return getTimeout().orElse(1800.0);
    }
}
