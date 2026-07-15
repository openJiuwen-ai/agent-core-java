/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import java.util.function.Supplier;

/**
 * Manager for AgentGroup resource providers.
 * Mirrors Python's {@code AgentGroupMgr} in {@code resources_manager/agent_group_manager.py}.
 * 
 * @since 0.1.7
 */
public class AgentGroupMgr<T> extends AbstractManager<T> {
    /**
     * addAgentGroup.
     * 
     * @param agentGroupId agentGroupId
     * @param agentGroup agentGroup
     * @since 0.1.7
     */
    public void addAgentGroup(String agentGroupId, Supplier<? extends T> agentGroup) {
        registerResourceProvider(agentGroupId, agentGroup);
    }

    /**
     * removeAgentGroup.
     * 
     * @param agentGroupId agentGroupId
     * @return the result
     * @since 0.1.7
     */
    public Supplier<? extends T> removeAgentGroup(String agentGroupId) {
        return unregisterResourceProvider(agentGroupId);
    }

    /**
     * getAgentGroup.
     * 
     * @param agentGroupId agentGroupId
     * @return the result
     * @since 0.1.7
     */
    public T getAgentGroup(String agentGroupId) {
        return getResource(agentGroupId);
    }
}
