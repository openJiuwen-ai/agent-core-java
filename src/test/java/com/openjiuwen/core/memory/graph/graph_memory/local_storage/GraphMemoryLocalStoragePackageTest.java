/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory.local_storage;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GraphMemoryLocalStoragePackageTest {

    @Test
    void exposesDefaultGraphStorageDir() throws Exception {
        Constructor<GraphMemoryLocalStoragePackage> constructor =
                GraphMemoryLocalStoragePackage.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(GraphMemoryLocalStoragePackage.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
        assertThat(GraphMemoryLocalStoragePackage.DEFAULT_GRAPH_STORAGE_DIR).isNotBlank();
        assertThat(GraphMemoryLocalStoragePackage.DEFAULT_GRAPH_STORAGE_DIR.replace('\\', '/'))
                .contains("/local_storage");
    }
}
