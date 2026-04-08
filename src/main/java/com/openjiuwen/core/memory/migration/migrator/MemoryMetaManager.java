/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import com.openjiuwen.core.memory.manage.mem_model.SqlDbStore;

import java.util.*;

/**
 * Manages memory_meta table for tracking migration schema versions.
 */
public class MemoryMetaManager {

    private final SqlDbStore sqlDb;
    private static final String META_TABLE = "memory_meta";

    public MemoryMetaManager(SqlDbStore sqlDb) {
        this.sqlDb = sqlDb;
    }

    public void add(String tableName, String schemaVersion) {
        if (tableName == null || tableName.isEmpty() || schemaVersion == null || schemaVersion.isEmpty()) {
            return;
        }
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("table_name", tableName);
        conditions.put("schema_version", schemaVersion);
        if (sqlDb.exist(META_TABLE, conditions)) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("table_name", tableName);
        data.put("schema_version", schemaVersion);
        sqlDb.write(META_TABLE, data);
    }

    public boolean deleteByTableName(String tableName) {
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("table_name", tableName);
        return sqlDb.delete(META_TABLE, conditions);
    }

    public List<Map<String, Object>> getByTableName(String tableName) {
        Map<String, List<Object>> conditions = new LinkedHashMap<>();
        conditions.put("table_name", new ArrayList<>(List.of(tableName)));
        List<Map<String, Object>> results = sqlDb.conditionGet(META_TABLE, conditions, null);
        return (results != null && !results.isEmpty()) ? results : null;
    }
}
