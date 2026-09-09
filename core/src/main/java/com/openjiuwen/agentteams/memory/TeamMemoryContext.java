/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.memory;

import com.openjiuwen.agentteams.schema.team.TeamMemoryConfig;

/**
 * Complete context used to create an optional team memory integration.
 *
 * @param config team memory configuration
 * @param member stable team member identity
 * @param execution runtime resources
 * @since 0.1.7
 */
public record TeamMemoryContext(TeamMemoryConfig config, TeamMemberContext member, TeamExecutionContext execution) {
}
