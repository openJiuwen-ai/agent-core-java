/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import java.util.Map;

/**
 * Public interface ModelAllocator used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public interface ModelAllocator {
    /**
     * allocate.
     * 
     * @param modelName modelName
     * @return the result
     * @since 0.1.7
     */
    Allocation allocate(String modelName);

    /**
     * allocate.
     * 
     * @return the result
     * @since 0.1.7
     */
    default Allocation allocate() {
        return allocate(null);
    }

    /**
     * stateDict.
     * 
     * @return the result
     * @since 0.1.7
     */
    Map<String, Object> stateDict();

    /**
     * loadStateDict.
     * 
     * @param state state
     * @since 0.1.7
     */
    void loadStateDict(Map<String, Object> state);
}
