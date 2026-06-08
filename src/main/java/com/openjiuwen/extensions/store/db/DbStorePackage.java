/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.db;

/**
 * Package bridge for DB store exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/extensions/store/db/__init__.py}.
 */
public final class DbStorePackage {

    public static final String PYTHON_MODULE = "openjiuwen/extensions/store/db/__init__.py";
    public static final Class<GaussDbStore> GAUSS_DB_STORE = GaussDbStore.class;

    private DbStorePackage() {
    }
}
