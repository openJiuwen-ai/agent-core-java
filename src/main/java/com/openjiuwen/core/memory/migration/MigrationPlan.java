/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.memory.migration.operation.OperationRegistry;

/**
 * Global migration registries for SQL, vector, and KV operations.
 * Users register migration operations here following the pattern in migration_plan.py.
 */
public final class MigrationPlan {

    private static final OperationRegistry SQL_REGISTRY = new OperationRegistry();
    private static final OperationRegistry VECTOR_REGISTRY = new OperationRegistry();
    private static final OperationRegistry KV_REGISTRY = new OperationRegistry();

    private MigrationPlan() {}

    public static OperationRegistry getSqlRegistry() {
        return SQL_REGISTRY;
    }

    public static OperationRegistry getVectorRegistry() {
        return VECTOR_REGISTRY;
    }

    public static OperationRegistry getKvRegistry() {
        return KV_REGISTRY;
    }
}
