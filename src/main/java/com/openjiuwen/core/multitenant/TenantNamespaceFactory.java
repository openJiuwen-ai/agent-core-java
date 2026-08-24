/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

/**
 * Functional interface for computing a tenant-scoped namespace from a raw key.
 *
 * @since 0.1.7
 */
@FunctionalInterface
public interface TenantNamespaceFactory {
    /**
     * Compute the tenant-scoped namespace for the given raw key.
     *
     * @param ctx the current tenant context
     * @param rawKey the original key
     * @return the resolved namespace string
     * @since 0.1.7
     */
    String namespace(TenantContext ctx, String rawKey);
}
