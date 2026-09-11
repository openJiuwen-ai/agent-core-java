/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.memory;

import java.util.ServiceLoader;

/**
 * Resolves the optional Agent Teams memory integration.
 *
 * @since 0.1.7
 */
public final class TeamMemoryResolver {
    private TeamMemoryResolver() {
    }

    /**
     * Creates the configured team memory integration.
     *
     * @param context team memory creation context
     * @return the created integration
     * @throws IllegalStateException when the Memory module is not present
     * @since 0.1.7
     */
    public static TeamMemory require(TeamMemoryContext context) {
        return ServiceLoader.load(TeamMemoryProvider.class).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Team memory is enabled but agent-core-memory-java is not available"))
                .create(context);
    }
}
