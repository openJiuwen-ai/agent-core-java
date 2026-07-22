/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStoreFactory;

import java.util.Map;

/**
 * KvTodoStorageProvider.
 *
 * @since 0.1.7
 */
public class KvTodoStorageProvider implements TodoStorageProvider {
    @Override
    public String typeName() {
        return "kv";
    }

    @Override
    public TodoStorage create(Map<String, Object> conf) {
        if (conf != null && conf.get("sharedKvStore") instanceof BaseKVStore) {
            return new KvTodoStorage((BaseKVStore) conf.get("sharedKvStore"));
        }
        String kvStoreType = "in_memory";
        if (conf != null) {
            Object raw = conf.getOrDefault("kvStoreType", "in_memory");
            if (raw instanceof String s) {
                kvStoreType = s;
            }
        }
        Map<String, Object> kvStoreConf = null;
        if (conf != null && conf.get("kvStoreConf") instanceof Map) {
            kvStoreConf = (Map<String, Object>) conf.get("kvStoreConf");
        }
        BaseKVStore kvStore = KVStoreFactory.create(kvStoreType, kvStoreConf != null ? kvStoreConf : Map.of());
        return new KvTodoStorage(kvStore);
    }
}
