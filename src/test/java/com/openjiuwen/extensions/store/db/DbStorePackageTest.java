/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's package export contract in
 * {@code openjiuwen/extensions/store/db/__init__.py}.
 */
class DbStorePackageTest {

    @Test
    void exposesExactPythonModulePath() {
        assertEquals("openjiuwen/extensions/store/db/__init__.py", DbStorePackage.PYTHON_MODULE);
    }

    @Test
    void exposesGaussDbStoreExport() {
        assertSame(GaussDbStore.class, DbStorePackage.GAUSS_DB_STORE);
    }
}
