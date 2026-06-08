/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryStoreTest {

    @Test
    void memoryStoreReadsByNestedPathAndSchema() {
        MemoryStore store = new MemoryStore();
        store.write(Map.of(
                "profile.name", "alice",
                "profile.age", 8,
                "active", true
        ));

        assertThat(store.read("profile.name")).isEqualTo("alice");
        assertThat(store.read(Map.of(
                "name", "${profile.name}",
                "age", "${profile.age}",
                "active", "${active}"
        ))).isEqualTo(Map.of(
                "name", "alice",
                "age", 8,
                "active", true
        ));
    }

    @Test
    void memoryStoreMergesNestedWrites() {
        MemoryStore store = new MemoryStore();
        store.write(Map.of("profile.name", "alice"));
        store.write(Map.of("profile.age", 9));

        assertThat(store.read("profile")).isEqualTo(Map.of(
                "name", "alice",
                "age", 9
        ));
    }

    @Test
    void fileStoreMatchesCurrentPythonStub() {
        FileStore store = new FileStore();

        assertThat(store.read("any")).isNull();
        store.write(Map.of("k", "v"));
        assertThat(store.read("any")).isNull();
    }
}
