/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.store.BaseDbStore;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code TestDefaultDbStore} in
 * {@code tests/unit_tests/core/foundation/store/test_default_db_store.py}.
 */
class DefaultDbStoreTest {

    @Test
    void constructorKeepsAsyncEngineInstance() {
        Object engine = new Object();

        DefaultDbStore<Object> store = new DefaultDbStore<>(engine);

        assertThat(store.getAsyncConn()).isSameAs(engine);
    }

    @Test
    void getAsyncEngineReturnsSameStoredInstance() {
        Object engine = new Object();

        DefaultDbStore<Object> store = new DefaultDbStore<>(engine);

        assertThat(store.getAsyncEngine()).isSameAs(engine);
        assertThat(store.getAsyncEngine()).isSameAs(store.getAsyncEngine());
    }

    @Test
    void defaultDbStoreRemainsABaseDbStore() {
        DefaultDbStore<Object> store = new DefaultDbStore<>(new Object());

        assertThat(store).isInstanceOf(BaseDbStore.class);
    }
}
