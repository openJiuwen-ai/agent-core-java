/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import java.util.Objects;

/**
 * Base memory unit - a single memory data item.
 * Corresponds to Python: manage/mem_model/memory_unit.py - BaseMemoryUnit
 */
public class BaseMemoryUnit {

    private final MemoryType memType;
    private final String userId;
    private final String scopeId;

    public BaseMemoryUnit(MemoryType memType, String userId, String scopeId) {
        this.memType = memType;
        this.userId = userId;
        this.scopeId = scopeId;
    }

    public MemoryType getMemType() {
        return memType;
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
        BaseMemoryUnit that = (BaseMemoryUnit) o;
        return memType == that.memType &&
               Objects.equals(userId, that.userId) &&
               Objects.equals(scopeId, that.scopeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memType, userId, scopeId);
    }

    @Override
    public String toString() {
        return "BaseMemoryUnit{" +
               "memType=" + memType +
               ", userId='" + userId + '\'' +
               ", scopeId='" + scopeId + '\'' +
               '}';
    }
}

