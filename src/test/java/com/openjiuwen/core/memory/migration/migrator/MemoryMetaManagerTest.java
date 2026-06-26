/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import com.openjiuwen.core.foundation.store.BaseDbStore;
import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Focused validation for {@link MemoryMetaManager}.
 *
 * <p>Mirrors Python's {@code MemoryMetaManager} in
 * {@code openjiuwen/core/memory/migration/migrator/memory_meta_manager.py}.</p>
 */
public final class MemoryMetaManagerTest {

    private MemoryMetaManagerTest() {
    }

    public static void main(String[] args) {
        verifiesAddReturnsWithoutStoreCallsForFalsyInput();
        verifiesAddSkipsExistingMeta();
        verifiesAddWritesMissingMeta();
        verifiesDeleteByTableName();
        verifiesGetByTableName();
        System.out.println("PASS MemoryMetaManagerTest");
    }

    private static void verifiesAddReturnsWithoutStoreCallsForFalsyInput() {
        RecordingSqlDbStore store = new RecordingSqlDbStore();
        MemoryMetaManager manager = new MemoryMetaManager(store);

        manager.add("", "1").join();
        manager.add("message", "").join();
        manager.add(null, "1").join();
        manager.add("message", null).join();

        require(store.existConditions == null, "falsy inputs do not query");
        require(store.writeData == null, "falsy inputs do not write");
    }

    private static void verifiesAddSkipsExistingMeta() {
        RecordingSqlDbStore store = new RecordingSqlDbStore();
        store.exists = true;
        MemoryMetaManager manager = new MemoryMetaManager(store);

        manager.add("message", "2", Map.of("ignored", "value")).join();

        require(store.existConditions.equals(linkedMap("table_name", "message", "schema_version", "2")),
                "exist conditions");
        require(store.writeData == null, "existing meta is not written");
    }

    private static void verifiesAddWritesMissingMeta() {
        RecordingSqlDbStore store = new RecordingSqlDbStore();
        MemoryMetaManager manager = new MemoryMetaManager(store);

        manager.add("message", "3").join();

        require(store.writeData.equals(linkedMap("table_name", "message", "schema_version", "3")),
                "write data");
    }

    private static void verifiesDeleteByTableName() {
        RecordingSqlDbStore store = new RecordingSqlDbStore();
        store.deleteResult = true;
        MemoryMetaManager manager = new MemoryMetaManager(store);

        require(manager.deleteByTableName("message").join(), "delete result");
        require(store.deleteConditions.equals(linkedMap("table_name", "message")), "delete conditions");
    }

    private static void verifiesGetByTableName() {
        RecordingSqlDbStore store = new RecordingSqlDbStore();
        MemoryMetaManager manager = new MemoryMetaManager(store);
        store.rows = List.of(linkedMap("table_name", "message", "schema_version", "4"));

        List<Map<String, Object>> rows = manager.getByTableName("message").join();

        require(rows.equals(store.rows), "get rows");
        require(store.conditionConditions.equals(Map.of("table_name", List.of("message"))), "condition list");

        store.rows = List.of();
        require(manager.getByTableName("message").join() == null, "empty result maps to null");
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
            require("memory_meta".equals(table), "exist table");
            this.existConditions = new LinkedHashMap<>(conditions);
            return CompletableFuture.completedFuture(exists);
        }

        @Override
        public CompletableFuture<Boolean> write(String table, Map<String, Object> data) {
            require("memory_meta".equals(table), "write table");
            this.writeData = new LinkedHashMap<>(data);
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> delete(String table, Map<String, ?> conditions) {
            require("memory_meta".equals(table), "delete table");
            this.deleteConditions = new LinkedHashMap<>(conditions);
            return CompletableFuture.completedFuture(deleteResult);
        }

        @Override
        public CompletableFuture<List<Map<String, Object>>> conditionGet(
                String table,
                Map<String, ?> conditions,
                List<String> columns
        ) {
            require("memory_meta".equals(table), "condition table");
            require(columns == null, "columns");
            this.conditionConditions = new LinkedHashMap<>(conditions);
            return CompletableFuture.completedFuture(rows);
        }
    }
}
