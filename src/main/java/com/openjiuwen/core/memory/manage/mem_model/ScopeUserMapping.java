/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

/**
 * Public row model matching the memory scope_user_mapping table.
 * 
 * @since 0.1.7
 */
public record ScopeUserMapping(String userId, String scopeId) {
}
