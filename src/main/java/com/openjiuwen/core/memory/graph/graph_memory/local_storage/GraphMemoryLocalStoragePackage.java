/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory.local_storage;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

/**
 * Graph storage directory defaults for graph memory files.
 *
 * <p>Mirrors Python's module in
 * {@code openjiuwen/core/memory/graph/graph_memory/local_storage/__init__.py}.</p>
 */
public final class GraphMemoryLocalStoragePackage {
    public static final String DEFAULT_GRAPH_STORAGE_DIR = resolveDefaultGraphStorageDir();

    private GraphMemoryLocalStoragePackage() {
    }

    private static String resolveDefaultGraphStorageDir() {
        URL resource = GraphMemoryLocalStoragePackage.class.getResource("");
        if (resource == null) {
            return "";
        }
        if ("file".equalsIgnoreCase(resource.getProtocol())) {
            try {
                return Path.of(resource.toURI()).toString();
            } catch (URISyntaxException ignored) {
                // Fall back to the raw path when the classpath URL cannot become a Path.
            }
        }
        return resource.getPath();
    }
}
