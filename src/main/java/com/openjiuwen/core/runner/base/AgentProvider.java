/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.base;

/**
 * Functional interface for providing agent instances.
 *
 * <p>Mirrors Python's agent provider pattern used by
 * {@link com.openjiuwen.core.multiagent.runtime.TeamRuntime} and
 * {@link com.openjiuwen.core.multiagent.BaseTeam}.</p>
 *
 * @param <T> the agent type
 */
@FunctionalInterface
public interface AgentProvider<T> {

    /**
     * Create or retrieve an agent instance.
     *
     * @return agent instance
     */
    T get();
}
