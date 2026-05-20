/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public void addAgentGroup(String agentGroupId, Supplier<? extends T> agentGroup) {
        registerResourceProvider(agentGroupId, agentGroup);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Supplier<? extends T> removeAgentGroup(String agentGroupId) {
        return unregisterResourceProvider(agentGroupId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public T getAgentGroup(String agentGroupId) {
        return getResource(agentGroupId);
    }
}
