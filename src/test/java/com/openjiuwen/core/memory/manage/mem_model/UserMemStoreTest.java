/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's old-framework user-memory-store coverage in
 * {@code tests/unit_tests/core/foundation/store/test_simple_memory_index.py}.
 */
class UserMemStoreTest {

    @Test
    void writeGetAllAndGetInRangePreserveOldFrameworkLayout() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        UserMemStore userMemStore = new UserMemStore(kvStore);

        assertThat(userMemStore.write("u1", "s1", id(1), data(id(1), "user_profile", "alpha")).join()).isTrue();
        assertThat(userMemStore.write("u1", "s1", id(2), data(id(2), "summary", "beta")).join()).isTrue();
        assertThat(userMemStore.write("u1", "s1", id(3), data(id(3), "user_profile", "gamma")).join()).isTrue();

        List<Map<String, Object>> all = userMemStore.getAll("u1", "s1").join();
        List<Map<String, Object>> userProfiles = userMemStore.getAll("u1", "s1", "user_profile").join();
        List<Map<String, Object>> range = userMemStore.getInRange("u1", "s1", 1, 3).join();

        assertThat(all).hasSize(3);
        assertThat(userProfiles).hasSize(2);
        assertThat(range).hasSize(2);
        assertThat(range)
                .extracting(entry -> entry.get("id"))
                .containsExactly(id(2), id(3));
    }

    @Test
    void writeRejectsEmptyOrDuplicatePayloads() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        UserMemStore userMemStore = new UserMemStore(kvStore);

        assertThat(userMemStore.write("u1", "s1", id(1), Map.of()).join()).isFalse();
        assertThat(userMemStore.write("u1", "s1", id(1), data(id(1), "user_profile", "alpha")).join()).isTrue();
        assertThat(userMemStore.write("u1", "s1", id(1), data(id(1), "user_profile", "duplicate")).join()).isFalse();
        List<Map<String, Object>> stored = userMemStore.getAll("u1", "s1").join();
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst()).containsEntry("text", "alpha");
    }

    @Test
    void updateAndBatchGetMergeStoredData() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        UserMemStore userMemStore = new UserMemStore(kvStore);

        userMemStore.write("u1", "s1", id(1), data(id(1), "user_profile", "alpha")).join();
        userMemStore.write("u1", "s1", id(2), data(id(2), "summary", "beta")).join();

        boolean updated = userMemStore.update(
                "u1",
                "s1",
                id(1),
                new LinkedHashMap<>(Map.of("score", 0.9, "text", "alpha-updated"))
        ).join();
        List<Map<String, Object>> values = userMemStore.batchGet("u1", "s1", List.of(id(1), id(2))).join();

        assertThat(updated).isTrue();
        assertThat(values).hasSize(2);
        assertThat(values.getFirst()).containsEntry("text", "alpha-updated").containsEntry("score", 0.9);
        assertThat(values.get(1)).containsEntry("text", "beta");
    }

    @Test
    void deleteAndBatchDeleteClearIdsAndPayloads() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        UserMemStore userMemStore = new UserMemStore(kvStore);

        userMemStore.write("u1", "s1", id(1), data(id(1), "user_profile", "alpha")).join();
        userMemStore.write("u1", "s1", id(2), data(id(2), "summary", "beta")).join();
        userMemStore.write("u1", "s1", id(3), data(id(3), "episodic_memory", "gamma")).join();

        userMemStore.delete("u1", "s1", id(2)).join();
        userMemStore.batchDelete("u1", "s1", List.of(id(1), id(3))).join();

        assertThat(userMemStore.get("u1", "s1", id(2)).join()).isNull();
        assertThat(userMemStore.getAll("u1", "s1")).isCompletedWithValue(null);
        assertThat(kvStore.exists("UMD/u1/s1/ids").join()).isFalse();
    }

    private static Map<String, Object> data(String id, String memType, String text) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("mem_type", memType);
        data.put("text", text);
        return data;
    }

    private static String id(int value) {
        return "%024d".formatted(value);
    }
}
