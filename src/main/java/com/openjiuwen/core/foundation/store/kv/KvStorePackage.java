/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import java.util.List;

/**
 * Package bridge for key-value store exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/core/foundation/store/kv/__init__.py}.
 * </p>
 */
public final class KvStorePackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/foundation/store/kv/__init__.py";
    public static final Class<ShelveStore> SHELVE_STORE = ShelveStore.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of("ShelveStore");

    private KvStorePackage() {
    }
}
