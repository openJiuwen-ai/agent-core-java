/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import java.util.*;

/**
 * Manages scope-user mapping records in the SQL database.
 */
public class ScopeUserMappingManager {

    private final SqlDbStore sqlDb;
    private static final String META_TABLE = "scope_user_mapping";

    public ScopeUserMappingManager(SqlDbStore sqlDb) {
        this.sqlDb = sqlDb;
    }

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

    public boolean deleteByScopeId(String scopeId) {
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("scope_id", scopeId);
        return sqlDb.delete(META_TABLE, conditions);
    }

    public List<Map<String, Object>> getByScopeId(String scopeId) {
        Map<String, List<Object>> conditions = new LinkedHashMap<>();
        conditions.put("scope_id", new ArrayList<>(List.of(scopeId)));
        List<Map<String, Object>> results = sqlDb.conditionGet(META_TABLE, conditions, null);
        return (results != null && !results.isEmpty()) ? results : null;
    }
}
