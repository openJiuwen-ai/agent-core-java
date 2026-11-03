/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's shelve-backed KV behavior in
 * {@code openjiuwen/core/foundation/store/kv/shelve_store.py}.
 */
class ShelveStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsValuesAndUnwrapsExclusiveGet() {
        Path dbPath = tempDir.resolve("kv-store.db");
        ShelveStore store = new ShelveStore(dbPath.toString());

        store.set("plain", "value").join();
        assertThat(store.exclusiveSet("lock", "token", 10).join()).isTrue();
        assertThat(store.exclusiveSet("lock", "other", 10).join()).isFalse();
        assertThat(store.get("plain").join()).isEqualTo("value");
        assertThat(store.get("lock").join()).isEqualTo("token");

        ShelveStore reopened = new ShelveStore(dbPath.toString());
        assertThat(reopened.get("plain").join()).isEqualTo("value");
        assertThat(reopened.get("lock").join()).isEqualTo("token");
    }

    @Test
    void prefixAndPipelineOperationsPreserveRawStoredValues() {
        ShelveStore store = new ShelveStore(tempDir.resolve("pipeline.db").toString());
        store.set("prefix:one", "alpha").join();
        store.exclusiveSet("prefix:lock", "beta", 10).join();

        Map<String, Object> prefixed = store.getByPrefix("prefix:").join();
        assertThat(prefixed.get("prefix:one")).isEqualTo("alpha");
        assertThat(prefixed.get("prefix:lock")).isInstanceOfAny(Map.class, Object.class);

        BasedKVStorePipeline pipeline = store.pipeline();
        pipeline.set("prefix:two", "gamma", null).join();
        pipeline.exists("prefix:two").join();
        pipeline.get("prefix:two").join();
        assertThat(pipeline.execute().join()).containsExactly(true, "gamma");

        assertThat(store.mget(List.of("prefix:one", "prefix:lock")).join()).hasSize(2);
        assertThat(store.batchDelete(List.of("prefix:one", "prefix:two"), 1).join()).isEqualTo(2);
        store.deleteByPrefix("prefix:", 1).join();
        assertThat(store.getByPrefix("prefix:").join()).isEmpty();
    }
}
