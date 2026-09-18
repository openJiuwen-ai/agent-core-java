/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Pipeline for batch operations on a KV store.
 * <p>
 * Mirrors Python's {@code BasedKVStorePipeline} in
 * {@code openjiuwen/core/foundation/store/base_kv_store.py}.
 */
public class BasedKVStorePipeline {

    private final Function<List<PipelineOperation>, CompletableFuture<List<Object>>> func;
    private final List<PipelineOperation> operations = new ArrayList<>();

    public BasedKVStorePipeline(Function<List<PipelineOperation>, CompletableFuture<List<Object>>> func) {
        this.func = func;
    }

    public CompletableFuture<Void> set(String key, Object value, Integer ttl) {
        operations.add(new PipelineOperation("set", key, value, ttl));
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> get(String key) {
        operations.add(new PipelineOperation("get", key, null, null));
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> exists(String key) {
        operations.add(new PipelineOperation("exists", key, null, null));
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<List<Object>> execute() {
        List<PipelineOperation> snapshot = List.copyOf(operations);
        return func.apply(snapshot).thenApply(results -> {
            operations.clear();
            return results;
        });
    }

    public List<PipelineOperation> getOperations() {
        return List.copyOf(operations);
    }

    public record PipelineOperation(String kind, String key, Object value, Integer ttl) {
    }
}
