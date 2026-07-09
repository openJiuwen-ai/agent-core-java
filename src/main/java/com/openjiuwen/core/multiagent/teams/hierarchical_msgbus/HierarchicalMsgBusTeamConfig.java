/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_msgbus;

import com.openjiuwen.core.multiagent.TeamConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public class HierarchicalMsgBusTeamConfig used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HierarchicalMsgBusTeamConfig extends TeamConfig {
    private AgentCard supervisorAgent;
    private Double timeout = 1800.0;

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, List<String>> hierarchy = new LinkedHashMap<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> supervisorDefinitions = new ArrayList<>();

    /**
     * Create a config with the supervisor agent and timeout.
     * 
     * @param supervisorAgent the supervisor agent card
     * @param timeout message timeout in seconds
     * @since 0.1.7
     */
    public HierarchicalMsgBusTeamConfig(AgentCard supervisorAgent, Double timeout) {
        this.supervisorAgent = supervisorAgent;
        this.timeout = timeout;
    }
}
