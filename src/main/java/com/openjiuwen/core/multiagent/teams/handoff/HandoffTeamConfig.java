/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;

import com.openjiuwen.core.multiagent.config.TeamConfig;

/**
 * Full configuration for HandoffTeam.
 * <p>
 * Mirrors Python's {@code HandoffTeamConfig} in 
 * {@code openjiuwen.core.multi_agent.teams.handoff.handoff_config}.
 * <p>
 * Extends TeamConfig with handoff-specific orchestration parameters.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HandoffTeamConfig extends TeamConfig {
    
    /** Handoff orchestration configuration. */
    @Builder.Default
    private HandoffConfig handoff = new HandoffConfig();
}