/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.kv;

import java.util.List;
import java.util.Map;

/**
 * Package bridge for KV store exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.store.kv} in
 * {@code openjiuwen/extensions/store/kv/__init__.py}.</p>
 */
public final class KvStorePackage {
    public static final String PYTHON_MODULE = "openjiuwen/extensions/store/kv/__init__.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of("RedisStore");
    public static final Class<RedisStore> REDIS_STORE = RedisStore.class;
    public static final Map<String, Class<?>> EXPORTED_TYPES = Map.of("RedisStore", RedisStore.class);

    private KvStorePackage() {
    }
}
