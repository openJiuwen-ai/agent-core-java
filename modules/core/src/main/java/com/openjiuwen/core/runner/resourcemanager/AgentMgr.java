/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import java.util.function.Supplier;

/**
 * Manager for Agent resource providers.
 * <p>
 * Mirrors Python's {@code AgentMgr} in {@code resources_manager/agent_manager.py}.
 * Note: RemoteAgent / distributed mode support is omitted here since those classes
 * (drunner package) are not yet implemented in Java.
 * 
 * @since 0.1.7
 */
public class AgentMgr<T> extends AbstractManager<T> {
    /**
     * addAgent.
     * 
     * @param agentId agentId
     * @param agent agent
     * @since 0.1.7
     */
    public void addAgent(String agentId, Supplier<? extends T> agent) {
        registerResourceProvider(agentId, agent);
    }

    /**
     * getAgent.
     * 
     * @param agentId agentId
     * @return the result
     * @since 0.1.7
     */
    public T getAgent(String agentId) {
        return getResource(agentId);
    }

    /**
     * removeAgent.
     * 
     * @param agentId agentId
     * @return the result
     * @since 0.1.7
     */
    public Supplier<? extends T> removeAgent(String agentId) {
        return unregisterResourceProvider(agentId);
    }
}
