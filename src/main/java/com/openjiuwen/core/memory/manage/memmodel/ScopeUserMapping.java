/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import java.util.Objects;

/**
 * Scope-User mapping entity.
 * Corresponds to Python: manage/mem_model/message.py - ScopeUserMapping
 *
 * <p>Python equivalent:
 * <pre>
 * class ScopeUserMapping(ScopeUserMixin, Base):
 *     __tablename__ = "scope_user_mapping"
 * </pre>
 */
public class ScopeUserMapping implements MessageBase {

    private static final String TABLE_NAME = "scope_user_mapping";

    private final String userId;
    private final String scopeId;

    public ScopeUserMapping(String userId, String scopeId) {
        this.userId = Objects.requireNonNull(userId, "userId is required");
        this.scopeId = Objects.requireNonNull(scopeId, "scopeId is required");
    }

    public static String getTableName() {
        return TABLE_NAME;
    }

    public String getUserId() {
        return userId;
    }

    public String getScopeId() {
        return scopeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScopeUserMapping that = (ScopeUserMapping) o;
        return Objects.equals(userId, that.userId) && Objects.equals(scopeId, that.scopeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, scopeId);
    }

    @Override
    public String toString() {
        return "ScopeUserMapping{" +
               "userId='" + userId + '\'' +
               ", scopeId='" + scopeId + '\'' +
               '}';
    }
}

