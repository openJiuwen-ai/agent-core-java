/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.foundation.store.BaseDbStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Focused validation for {@link ScopeUserMappingManager}.
 *
 * <p>Mirrors Python's {@code ScopeUserMappingManager} in
 * {@code openjiuwen/core/memory/manage/mem_model/scope_user_mapping_manager.py}.</p>
 */
public final class ScopeUserMappingManagerTest {

    private ScopeUserMappingManagerTest() {
    }

    public static void main(String[] args) {
        verifiesAddSkipsExistingMapping();
        verifiesAddWritesMissingMapping();
        verifiesDeleteByScopeId();
        verifiesGetByScopeId();
        System.out.println("PASS ScopeUserMappingManagerTest");
    }

    private static void verifiesAddSkipsExistingMapping() {
        RecordingSqlDbStore store = new RecordingSqlDbStore();
        store.exists = true;
        ScopeUserMappingManager manager = new ScopeUserMappingManager(store);

        manager.add(null, "").join();

        require(store.existConditions.equals(linkedMap("user_id", "", "scope_id", "")), "normalized conditions");
        require(store.writeData == null, "existing mapping is not written");
    }

    private static void verifiesAddWritesMissingMapping() {
        RecordingSqlDbStore store = new RecordingSqlDbStore();
        ScopeUserMappingManager manager = new ScopeUserMappingManager(store);

        manager.add("user-a", "scope-a", linkedMap("ignored", "value")).join();

        require(store.writeData.equals(linkedMap("user_id", "user-a", "scope_id", "scope-a")), "write data");
    }

    private static void verifiesDeleteByScopeId() {
        RecordingSqlDbStore store = new RecordingSqlDbStore();
        store.deleteResult = true;
        ScopeUserMappingManager manager = new ScopeUserMappingManager(store);

        require(manager.deleteByScopeId("scope-a").join(), "delete result");
        require(store.deleteConditions.equals(linkedMap("scope_id", "scope-a")), "delete conditions");
    }

    private static void verifiesGetByScopeId() {
        RecordingSqlDbStore store = new RecordingSqlDbStore();
        ScopeUserMappingManager manager = new ScopeUserMappingManager(store);
        store.rows = List.of(linkedMap("scope_id", "scope-a", "user_id", "user-a"));

        List<Map<String, Object>> rows = manager.getByScopeId("scope-a").join();

        require(rows.equals(store.rows), "get rows");
        require(store.conditionConditions.equals(Map.of("scope_id", List.of("scope-a"))), "condition list");

        store.rows = List.of();
        require(manager.getByScopeId("scope-a").join() == null, "empty result maps to null");
    }

    private static Map<String, Object> linkedMap(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingSqlDbStore extends SqlDbStore {

        private boolean exists;
        private boolean deleteResult;
        private Map<String, Object> existConditions;
        private Map<String, Object> writeData;
        private Map<String, ?> deleteConditions;
        private Map<String, ?> conditionConditions;
        private List<Map<String, Object>> rows;

        private RecordingSqlDbStore() {
            super(new BaseDbStore<>() {
                @Override
                public Object getAsyncEngine() {
                    return null;
                }
            });
        }

        @Override
        public CompletableFuture<Boolean> exist(String table, Map<String, Object> conditions) {
            require("scope_user_mapping".equals(table), "exist table");
            this.existConditions = new LinkedHashMap<>(conditions);
            return CompletableFuture.completedFuture(exists);
        }

        @Override
        public CompletableFuture<Boolean> write(String table, Map<String, Object> data) {
            require("scope_user_mapping".equals(table), "write table");
            this.writeData = new LinkedHashMap<>(data);
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> delete(String table, Map<String, ?> conditions) {
            require("scope_user_mapping".equals(table), "delete table");
            this.deleteConditions = new LinkedHashMap<>(conditions);
            return CompletableFuture.completedFuture(deleteResult);
        }

        @Override
        public CompletableFuture<List<Map<String, Object>>> conditionGet(
                String table,
                Map<String, ?> conditions,
                List<String> columns
        ) {
            require("scope_user_mapping".equals(table), "condition table");
            require(columns == null, "columns");
            this.conditionConditions = new LinkedHashMap<>(conditions);
            return CompletableFuture.completedFuture(rows);
        }
    }
}
