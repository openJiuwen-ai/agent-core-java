/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.memory;

/**
 * Service provider for the optional Agent Teams memory integration.
 *
 * @since 0.1.7
 */
public interface TeamMemoryProvider {
    /**
     * Creates a team memory integration for one team agent.
     *
     * @param context team memory creation context
     * @return the created integration
     * @since 0.1.7
     */
    TeamMemory create(TeamMemoryContext context);
}
