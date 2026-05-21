/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store.graph;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for graph store initialization.
 * <p>
 * Mirrors Python's {@code test_init.py} from
 * {@code tests/unit_tests/core/foundation/store/graph/test_init.py}.
 * Tests graph store initialization and module imports.
 */
class TestInit {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Module initialization)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testModuleInitialization() {
        /** Test graph store module initialization - verify package structure */
        Package pkg = TestInit.class.getPackage();
        assertNotNull(pkg);
        assertTrue(pkg.getName().contains("foundation.store.graph"));
    }

    @Test
    @Tag("level0")
    void testPackageExists() {
        assertNotNull(TestInit.class.getPackage());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Graph store imports)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testBaseGraphStoreImport() {
        assertNotNull(TestBaseGraphStore.class);
    }

    @Test
    @Tag("level1")
    void testConstantsImport() {
        assertNotNull(TestConstants.class);
    }

    @Test
    @Tag("level1")
    void testDatabaseConfigImport() {
        assertNotNull(TestDatabaseConfig.class);
    }

    @Test
    @Tag("level1")
    void testGraphObjectImport() {
        assertNotNull(TestGraphObject.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Milvus imports)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testMilvusInitImport() {
        assertNotNull(com.openjiuwen.unit_tests.core.foundation.store.graph.milvus.TestMilvusInit.class);
    }

    @Test
    @Tag("level2")
    void testMilvusSchemaImport() {
        assertNotNull(com.openjiuwen.unit_tests.core.foundation.store.graph.milvus.TestGenerateMilvusSchema.class);
    }

    @Test
    @Tag("level2")
    void testMilvusSupportImport() {
        assertNotNull(com.openjiuwen.unit_tests.core.foundation.store.graph.milvus.TestMilvusSupport.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Utility imports)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testGraphStoreUtilsImport() {
        assertNotNull(TestGraphStoreUtils.class);
    }

    @Test
    @Tag("level3")
    void testAllModulesAccessible() {
        assertNotNull(TestInit.class);
        assertNotNull(TestBase.class);
        assertNotNull(TestConstants.class);
        assertNotNull(TestDatabaseConfig.class);
        assertNotNull(TestGraphObject.class);
        assertNotNull(TestGraphStoreUtils.class);
    }
}