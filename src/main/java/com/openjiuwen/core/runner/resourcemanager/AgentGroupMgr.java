/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.resourcemanager;

import java.util.function.Supplier;

/**
 * Manager for AgentGroup resource providers.
 * Mirrors Python's {@code AgentGroupMgr} in {@code resources_manager/agent_group_manager.py}.
 *
 * @param <T> the agent group base type
 */
public class AgentGroupMgr<T> extends AbstractManager<T> {

    public void addAgentGroup(String agentGroupId, Supplier<? extends T> agentGroup) {
        registerResourceProvider(agentGroupId, agentGroup);
    }

    public Supplier<? extends T> removeAgentGroup(String agentGroupId) {
        return unregisterResourceProvider(agentGroupId);
    }

    public T getAgentGroup(String agentGroupId) {
        return getResource(agentGroupId);
    }
}
