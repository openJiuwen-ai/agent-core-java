/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.memory.migration.operation.OperationRegistry;

/**
 * Global migration registries for SQL, vector, and KV operations.
 * Users register migration operations here following the pattern in migration_plan.py.
 * 
 * @since 0.1.7
 */
public final class MigrationPlan {
    private static final OperationRegistry SQL_REGISTRY = new OperationRegistry();

    /**
     * OperationRegistry.
     * 
     * @since 0.1.7
     */
    private static final OperationRegistry VECTOR_REGISTRY = new OperationRegistry();

    /**
     * OperationRegistry.
     * 
     * @since 0.1.7
     */
    private static final OperationRegistry KV_REGISTRY = new OperationRegistry();

    /**
     * MigrationPlan.
     * 
     * @since 0.1.7
     */
    private MigrationPlan() {
    }

    /**
     * getSqlRegistry.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static OperationRegistry getSqlRegistry() {
        return SQL_REGISTRY;
    }

    /**
     * getVectorRegistry.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static OperationRegistry getVectorRegistry() {
        return VECTOR_REGISTRY;
    }

    /**
     * getKvRegistry.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static OperationRegistry getKvRegistry() {
        return KV_REGISTRY;
    }
}
