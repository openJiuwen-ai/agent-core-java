/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.models;

import com.openjiuwen.agent_teams.agent.RecoveryManager;
import java.util.Map;

/**
 * Allocates one pool entry per call.
 *
 * <p>Mirrors Python's {@code ModelAllocator} protocol in
 * {@code openjiuwen/agent_teams/models/allocator.py}.</p>
 */
public interface ModelAllocator
        extends com.openjiuwen.agent_teams.agent.AgentConfigurator.ModelAllocator,
        RecoveryManager.StatefulAllocator {

    @Override
    Allocation allocate(String modelName);

    default Allocation allocate() {
        return allocate(null);
    }

    @Override
    Map<String, Object> stateDict();

    @Override
    void loadStateDict(Map<String, Object> state);
}
