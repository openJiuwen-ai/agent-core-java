/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseDbStoreTest {

    @Test
    void exposesConfiguredAsyncEngine() {
        BaseDbStore<String> store = new BaseDbStore<>() {
            @Override
            public String getAsyncEngine() {
                return "engine";
            }
        };

        assertThat(store.getAsyncEngine()).isEqualTo("engine");
    }
}
