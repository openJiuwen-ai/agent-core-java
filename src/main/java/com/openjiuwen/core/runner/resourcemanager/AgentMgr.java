/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
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
 * @param <T> the agent base type
 */
public class AgentMgr<T> extends AbstractManager<T> {

    public void addAgent(String agentId, Supplier<? extends T> agent) {
        registerResourceProvider(agentId, agent);
    }

    public T getAgent(String agentId) {
        return getResource(agentId);
    }

    public Supplier<? extends T> removeAgent(String agentId) {
        return unregisterResourceProvider(agentId);
    }
}
