/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.graph.store.InMemoryStore;

import java.util.List;

/**
 * Package bridge for graph exports.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph} in
 * {@code openjiuwen/core/graph/__init__.py}.
 * </p>
 */
public final class GraphPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/graph/__init__.py";
    public static final Class<InMemoryStore> IN_MEMORY_STORE = InMemoryStore.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of("InMemoryStore");

    private GraphPackage() {
    }
}
