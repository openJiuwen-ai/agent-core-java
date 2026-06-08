/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for all KV-store backends.
 * <p>
 * Mirrors Python's {@code BaseKVStore} in
 * {@code openjiuwen/core/foundation/store/base_kv_store.py}.
 */
public abstract class BaseKVStore {

    public abstract CompletableFuture<Void> set(String key, Object value);

    public abstract CompletableFuture<Boolean> exclusiveSet(String key, Object value, Integer expiry);

    public abstract CompletableFuture<Object> get(String key);

    public abstract CompletableFuture<Boolean> exists(String key);

    public abstract CompletableFuture<Void> delete(String key);

    public abstract CompletableFuture<Map<String, Object>> getByPrefix(String prefix);

    public abstract CompletableFuture<Void> deleteByPrefix(String prefix, Integer batchSize);

    public abstract CompletableFuture<List<Object>> mget(List<String> keys);

    public abstract CompletableFuture<Integer> batchDelete(List<String> keys, Integer batchSize);

    public abstract BasedKVStorePipeline pipeline();
}
