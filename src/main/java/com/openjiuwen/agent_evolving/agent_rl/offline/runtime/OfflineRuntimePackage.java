/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.RLRail;

import java.util.List;

/**
 * Agent runtime package marker for offline RL rollout generation.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.agent_rl.offline.runtime} module in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/runtime/__init__.py}.</p>
 */
public final class OfflineRuntimePackage {

    public static final String DESCRIPTION = """
            Agent runtime for offline RL rollout generation.

            This module provides trajectory collection capabilities for RL training.
            The RLRail class is the primary implementation based on EvolutionRail.
            """;

    public static final List<String> EXPORTED_NAMES = List.of("RLRail");

    public static final List<Class<?>> EXPORTED_TYPES = List.of(RLRail.class);

    private OfflineRuntimePackage() {
    }
}
