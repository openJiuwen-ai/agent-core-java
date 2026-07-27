/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.memory_call;

import java.util.List;

/**
 * Package bridge for memory optimizer exports.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.memory_call}
 * in {@code openjiuwen/agent_evolving/optimizer/memory_call/__init__.py}.
 * </p>
 */
public final class MemoryCallOptimizerPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/agent_evolving/optimizer/memory_call/__init__.py";
    public static final Class<MemoryOptimizerBase> MEMORY_OPTIMIZER_BASE = MemoryOptimizerBase.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of("MemoryOptimizerBase");

    private MemoryCallOptimizerPackage() {
    }
}
