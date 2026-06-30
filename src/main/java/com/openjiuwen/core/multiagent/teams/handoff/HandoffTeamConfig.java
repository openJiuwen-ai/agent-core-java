/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.multiagent.TeamConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * Public class HandoffTeamConfig used by the Java parity implementation.
 *
 * @since 1.0
 */
@EqualsAndHashCode(callSuper = true)
public class HandoffTeamConfig extends TeamConfig {
    private HandoffConfig handoff = new HandoffConfig();
}
