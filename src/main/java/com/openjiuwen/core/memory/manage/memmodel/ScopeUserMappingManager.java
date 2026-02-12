/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manager for scope-user mappings.
 * <p>
 * Corresponds to Python: manage/mem_model/scope_user_mapping_manager.py
 */
public class ScopeUserMappingManager {

    private final SqlDbStore sqlDb;
    private final String metaTable = "scope_user_mapping";

    /**
     * Create a new ScopeUserMappingManager.
     *
     * @param sqlDbStore the SQL database store
     */
    public ScopeUserMappingManager(SqlDbStore sqlDbStore) {
        this.sqlDb = sqlDbStore;
    }

    /**
     * Add a scope-user mapping.
     * Does nothing if the mapping already exists.
     *
     * @param userId the user ID
     * @param scopeId the scope ID
     * @return CompletableFuture that completes when the operation is done
     */
    public CompletableFuture<Void> add(String userId, String scopeId) {
        Map<String, Object> data = new HashMap<>();
        data.put("user_id", userId != null ? userId : "");
        data.put("scope_id", scopeId != null ? scopeId : "");

        Map<String, Object> conditions = new HashMap<>();
        conditions.put("user_id", data.get("user_id"));
        conditions.put("scope_id", data.get("scope_id"));

        return sqlDb.exist(metaTable, conditions)
                .thenCompose(exists -> {
                    if (exists) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return sqlDb.write(metaTable, data)
                            .thenApply(success -> null);
                });
    }

    /**
     * Delete all mappings for a scope.
     *
     * @param scopeId the scope ID
     * @return CompletableFuture containing true if any mappings were deleted
     */
    public CompletableFuture<Boolean> deleteByScopeId(String scopeId) {
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("scope_id", scopeId);
        return sqlDb.delete(metaTable, conditions);
    }

    /**
     * Get all mappings for a scope.
     *
     * @param scopeId the scope ID
     * @return CompletableFuture containing list of mappings or null if none found
     */
    public CompletableFuture<List<Map<String, Object>>> getByScopeId(String scopeId) {
        Map<String, List<Object>> conditions = new HashMap<>();
        conditions.put("scope_id", List.of(scopeId));

        return sqlDb.conditionGet(metaTable, conditions, null)
                .thenApply(results -> {
                    if (results == null || results.isEmpty()) {
                        return null;
                    }
                    return results;
                });
    }
}

