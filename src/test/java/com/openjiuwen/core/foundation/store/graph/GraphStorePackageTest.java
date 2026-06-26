/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's package facade in
 * {@code openjiuwen/core/foundation/store/graph/__init__.py}.
 *
 * <p>Mirrors Python's {@code TestExports.test_exports} in
 * {@code tests/unit_tests/core/foundation/store/graph/test_init.py}.</p>
 */
class GraphStorePackageTest {

    @Test
    void exportedSymbolsMatchPythonAllOrder() {
        assertThat(GraphStorePackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/core/foundation/store/graph/__init__.py");

        assertThat(GraphStorePackage.all()).containsExactly(
                "GraphStore",
                "GraphStoreFactory",
                "GraphConfig",
                "GraphStoreIndexConfig",
                "GraphStoreStorageConfig",
                "ENTITY_COLLECTION",
                "EPISODE_COLLECTION",
                "RELATION_COLLECTION",
                "Entity",
                "Episode",
                "Relation"
        );
    }

    @Test
    void keyExportsMapToTranslatedJavaTypes() {
        assertThat(GraphStorePackage.sourceFor("GraphStoreFactory"))
                .isEqualTo("openjiuwen.core.foundation.store.graph.base.GraphStoreFactory");
        assertThat(GraphStorePackage.javaTypeNameFor("GraphStoreFactory"))
                .isEqualTo("com.openjiuwen.core.foundation.store.graph.GraphStoreFactory");
        assertThat(GraphStorePackage.sourceFor("ENTITY_COLLECTION"))
                .isEqualTo("openjiuwen.core.foundation.store.graph.constants.ENTITY_COLLECTION");
        assertThat(GraphStorePackage.javaTypeNameFor("ENTITY_COLLECTION"))
                .isEqualTo("com.openjiuwen.core.foundation.store.graph.GraphStoreConstants#ENTITY_COLLECTION");
        assertThat(GraphStorePackage.javaTypeNameFor("Relation"))
                .isEqualTo("com.openjiuwen.core.foundation.store.graph.Relation");
    }

    @Test
    void unknownSymbolIsNotExposed() {
        assertThat(GraphStorePackage.exports("BaseGraphObject")).isFalse();
        assertThat(GraphStorePackage.sourceFor("missing")).isNull();
        assertThat(GraphStorePackage.javaTypeNameFor("missing")).isNull();
    }

    @Test
    void exportedSymbolsAreImmutable() {
        assertThatThrownBy(() -> GraphStorePackage.all().add("unexpected"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
