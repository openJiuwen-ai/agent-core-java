/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory.local_storage;

import java.net.URL;
import java.nio.file.Path;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestDefaultGraphStorageDir} in
 * {@code tests/unit_tests/core/memory/graph/graph_memory/test_local_storage.py}.
 */
class GraphMemoryLocalStoragePackageTest {

    @Test
    void defaultGraphStorageDirIsNonEmptyPathString() throws Exception {
        Constructor<GraphMemoryLocalStoragePackage> constructor =
                GraphMemoryLocalStoragePackage.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(GraphMemoryLocalStoragePackage.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
        assertThat(GraphMemoryLocalStoragePackage.DEFAULT_GRAPH_STORAGE_DIR).isNotBlank();
    }

    @Test
    void defaultGraphStorageDirResolvesToLocalStoragePackageDir() throws Exception {
        URL packageResource = GraphMemoryLocalStoragePackage.class.getResource("");

        assertThat(packageResource).isNotNull();
        assertThat(Path.of(GraphMemoryLocalStoragePackage.DEFAULT_GRAPH_STORAGE_DIR).normalize())
                .isEqualTo(Path.of(packageResource.toURI()).normalize());
        assertThat(GraphMemoryLocalStoragePackage.DEFAULT_GRAPH_STORAGE_DIR.replace('\\', '/'))
                .contains("/local_storage");
    }
}
