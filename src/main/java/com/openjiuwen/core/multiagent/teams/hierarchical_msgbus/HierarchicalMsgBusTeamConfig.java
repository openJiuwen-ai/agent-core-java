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

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * Public class HierarchicalMsgBusTeamConfig used by the Java parity implementation.
 *
 * @since 1.0
 */
@EqualsAndHashCode(callSuper = true)
public class HierarchicalMsgBusTeamConfig extends TeamConfig {
    private AgentCard supervisorAgent;
    private Double timeout = 1800.0;
}
