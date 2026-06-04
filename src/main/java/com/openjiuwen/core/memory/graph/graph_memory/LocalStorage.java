/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import java.nio.file.Path;

/**
 * Local storage constants for graph memory resources.
 *
 * <p>Mirrors Python's {@code local_storage} module in
 * {@code openjiuwen.core.memory.graph.graph_memory.local_storage}.
 */
public final class LocalStorage {

    public static final String DEFAULT_GRAPH_STORAGE_DIR =
            Path.of("src", "main", "java", "com", "openjiuwen", "core", "memory", "graph", "graph_memory")
                    .toAbsolutePath()
                    .normalize()
                    .toString();

    private LocalStorage() {
    }
}
