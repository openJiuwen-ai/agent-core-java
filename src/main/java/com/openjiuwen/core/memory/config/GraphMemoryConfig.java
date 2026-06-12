/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

/**
 * Module-level constants for graph memory configuration.
 * <p>
 * Mirrors Python's {@code DEFAULT_STRATEGY} in
 * {@code openjiuwen/core/memory/config/graph.py}.
 * </p>
 */
public final class GraphMemoryConfig {

    public static final String PYTHON_MODULE = "openjiuwen/core/memory/config/graph.py";
    public static final AddMemStrategy DEFAULT_STRATEGY = new AddMemStrategy();

    private GraphMemoryConfig() {
    }
}
