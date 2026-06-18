/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code MemoryMetaManager} in
 * {@code openjiuwen/core/memory/migration/migrator/memory_meta_manager.py}.
 */
public class MemoryMetaManager {

    private static final String META_TABLE = "memory_meta";

    private final SqlDbStore sqlDb;

    public MemoryMetaManager(SqlDbStore sqlDbStore) {
        this.sqlDb = sqlDbStore;
    }

    public CompletableFuture<Void> add(String tableName, String schemaVersion) {
        return add(tableName, schemaVersion, Map.of());
    }

    public CompletableFuture<Void> add(String tableName, String schemaVersion, Map<String, ?> kwargs) {
        if (isFalsyString(tableName) || isFalsyString(schemaVersion)) {
            return CompletableFuture.completedFuture(null);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("table_name", tableName);
        data.put("schema_version", schemaVersion);

        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("table_name", data.get("table_name"));
        conditions.put("schema_version", data.get("schema_version"));

        return sqlDb.exist(META_TABLE, conditions).thenCompose(exists -> {
            if (Boolean.TRUE.equals(exists)) {
                return CompletableFuture.completedFuture(null);
            }
            return sqlDb.write(META_TABLE, data).thenApply(ignored -> null);
        });
    }

    public CompletableFuture<Boolean> deleteByTableName(String tableName) {
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("table_name", tableName);
        return sqlDb.delete(META_TABLE, conditions);
    }

    public CompletableFuture<List<Map<String, Object>>> getByTableName(String tableName) {
        List<Object> tableNames = new ArrayList<>(1);
        tableNames.add(tableName);

        Map<String, List<Object>> conditions = new LinkedHashMap<>();
        conditions.put("table_name", tableNames);

        return sqlDb.conditionGet(META_TABLE, conditions, null).thenApply(results -> {
            if (results == null || results.isEmpty()) {
                return null;
            }
            return results;
        });
    }

    private static boolean isFalsyString(String value) {
        return value == null || value.isEmpty();
    }
}
