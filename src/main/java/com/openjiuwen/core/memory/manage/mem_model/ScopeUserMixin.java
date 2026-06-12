/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

/**
 * Shared user/scope columns for memory SQL models.
 *
 * <p>Mirrors Python's {@code ScopeUserMixin} in
 * {@code openjiuwen/core/memory/manage/mem_model/db_model.py}.</p>
 */
public abstract class ScopeUserMixin {

    private String userId;
    private String scopeId;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }
}
