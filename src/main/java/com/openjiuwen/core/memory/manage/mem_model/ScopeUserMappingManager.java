/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages scope-user mapping records in the SQL database.
 * 
 * @since 0.1.7
 */
public class ScopeUserMappingManager {
    private final SqlDbStore sqlDb;
    private static final String META_TABLE = "scope_user_mapping";

    /**
     * ScopeUserMappingManager.
     * 
     * @param sqlDb sqlDb
     * @since 0.1.7
     */
    public ScopeUserMappingManager(SqlDbStore sqlDb) {
        this.sqlDb = sqlDb;
    }

    /**
     * add.
     * 
     * @param userId userId
     * @param scopeId scopeId
     * @since 0.1.7
     */
    public void add(String userId, String scopeId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", userId != null ? userId : "");
        data.put("scope_id", scopeId != null ? scopeId : "");

        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("user_id", data.get("user_id"));
        conditions.put("scope_id", data.get("scope_id"));

        boolean exists = sqlDb.exist(META_TABLE, conditions);
        if (exists) {
            return;
        }
        sqlDb.write(META_TABLE, data);
    }

    /**
     * deleteByScopeId.
     * 
     * @param scopeId scopeId
     * @return the result
     * @since 0.1.7
     */
    public boolean deleteByScopeId(String scopeId) {
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("scope_id", scopeId);
        return sqlDb.delete(META_TABLE, conditions);
    }

    /**
     * getByScopeId.
     * 
     * @param scopeId scopeId
     * @return the result
     * @since 0.1.7
     */
    public List<Map<String, Object>> getByScopeId(String scopeId) {
        Map<String, List<Object>> conditions = new LinkedHashMap<>();
        conditions.put("scope_id", new ArrayList<>(List.of(scopeId)));
        List<Map<String, Object>> results = sqlDb.conditionGet(META_TABLE, conditions, null);
        return (results != null && !results.isEmpty()) ? results : null;
    }
}
