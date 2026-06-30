/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import java.util.Map;

/**
 * Public interface ModelAllocator used by the Java parity implementation.
 *
 * @since 1.0
 */
public interface ModelAllocator {
    Allocation allocate(String modelName);

    default Allocation allocate() {
        return allocate(null);
    }

    Map<String, Object> stateDict();

    void loadStateDict(Map<String, Object> state);
}
