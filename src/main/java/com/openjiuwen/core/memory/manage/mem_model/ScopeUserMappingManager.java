/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages scope-user mapping rows in the SQL database.
 *
 * <p>Mirrors Python's {@code ScopeUserMappingManager} in
 * {@code openjiuwen/core/memory/manage/mem_model/scope_user_mapping_manager.py}.</p>
 */
public class ScopeUserMappingManager {

    private static final String META_TABLE = "scope_user_mapping";

    private final SqlDbStore sqlDb;

    public ScopeUserMappingManager(SqlDbStore sqlDb) {
        this.sqlDb = sqlDb;
    }

    public CompletableFuture<Void> add(String userId, String scopeId) {
        return add(userId, scopeId, Map.of());
    }

    public CompletableFuture<Void> add(String userId, String scopeId, Map<String, Object> kwargs) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", emptyIfMissing(userId));
        data.put("scope_id", emptyIfMissing(scopeId));

        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("user_id", data.get("user_id"));
        conditions.put("scope_id", data.get("scope_id"));

        return sqlDb.exist(META_TABLE, conditions).thenCompose(exists -> {
            if (Boolean.TRUE.equals(exists)) {
                return CompletableFuture.completedFuture(null);
            }
            return sqlDb.write(META_TABLE, data).thenApply(ignored -> null);
        });
    }

    public CompletableFuture<Boolean> deleteByScopeId(String scopeId) {
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("scope_id", scopeId);
        return sqlDb.delete(META_TABLE, conditions);
    }

    public CompletableFuture<List<Map<String, Object>>> getByScopeId(String scopeId) {
        Map<String, List<Object>> conditions = new LinkedHashMap<>();
        List<Object> scopeIds = new ArrayList<>(1);
        scopeIds.add(scopeId);
        conditions.put("scope_id", scopeIds);

        return sqlDb.conditionGet(META_TABLE, conditions, null).thenApply(results -> {
            if (results == null || results.isEmpty()) {
                return null;
            }
            return results;
        });
    }

    private static String emptyIfMissing(String value) {
        return value == null || value.isEmpty() ? "" : value;
    }
}
