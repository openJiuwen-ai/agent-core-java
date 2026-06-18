/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.kv;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's {@code openjiuwen.extensions.store.kv} package export contract in
 * {@code openjiuwen/extensions/store/kv/__init__.py}.
 */
class KvStorePackageTest {

    @Test
    void exposesExactPythonModulePath() {
        assertEquals("openjiuwen/extensions/store/kv/__init__.py", KvStorePackage.PYTHON_MODULE);
    }

    @Test
    void exposesRedisStoreExport() {
        assertEquals(List.of("RedisStore"), KvStorePackage.EXPORTED_SYMBOLS);
        assertSame(RedisStore.class, KvStorePackage.REDIS_STORE);
        assertSame(RedisStore.class, KvStorePackage.EXPORTED_TYPES.get("RedisStore"));
    }
}
