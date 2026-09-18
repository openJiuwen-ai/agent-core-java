/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class BaseKVStoreTest {

    @Test
    void pipelineQueuesOperationsAndClearsAfterExecute() {
        BasedKVStorePipeline pipeline = new BasedKVStorePipeline(operations -> CompletableFuture.completedFuture(
                operations.stream().map(BasedKVStorePipeline.PipelineOperation::kind).map(Object.class::cast).toList()
        ));

        pipeline.set("k1", "v1", null).join();
        pipeline.get("k2").join();
        pipeline.exists("k3").join();

        assertThat(pipeline.getOperations())
                .extracting(BasedKVStorePipeline.PipelineOperation::kind)
                .containsExactly("set", "get", "exists");

        assertThat(pipeline.execute().join()).containsExactly("set", "get", "exists");
        assertThat(pipeline.getOperations()).isEmpty();
    }

    @Test
    void subclassExposesConfiguredPipelineAndAsyncMethods() {
        BaseKVStore store = new BaseKVStore() {
            private final BasedKVStorePipeline pipeline = new BasedKVStorePipeline(
                    operations -> CompletableFuture.completedFuture(List.of())
            );

            @Override
            public CompletableFuture<Void> set(String key, Object value) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Boolean> exclusiveSet(String key, Object value, Integer expiry) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletableFuture<Object> get(String key) {
                return CompletableFuture.completedFuture("value");
            }

            @Override
            public CompletableFuture<Boolean> exists(String key) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletableFuture<Void> delete(String key) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Map<String, Object>> getByPrefix(String prefix) {
                return CompletableFuture.completedFuture(Map.of(prefix + "1", "value"));
            }

            @Override
            public CompletableFuture<Void> deleteByPrefix(String prefix, Integer batchSize) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<List<Object>> mget(List<String> keys) {
                return CompletableFuture.completedFuture(List.of("value"));
            }

            @Override
            public CompletableFuture<Integer> batchDelete(List<String> keys, Integer batchSize) {
                return CompletableFuture.completedFuture(keys.size());
            }

            @Override
            public BasedKVStorePipeline pipeline() {
                return pipeline;
            }
        };

        assertThat(store.get("key").join()).isEqualTo("value");
        assertThat(store.exists("key").join()).isTrue();
        assertThat(store.batchDelete(List.of("a", "b"), null).join()).isEqualTo(2);
        assertThat(store.pipeline()).isNotNull();
    }
}
