/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

/**
 * Memory SQL row that maps scopes to users.
 *
 * <p>Mirrors Python's {@code ScopeUserMapping} in
 * {@code openjiuwen/core/memory/manage/mem_model/db_model.py}.</p>
 */
public class ScopeUserMapping extends ScopeUserMixin {

    public static final String TABLE_NAME = "scope_user_mapping";
}
