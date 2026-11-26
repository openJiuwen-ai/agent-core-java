/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves the workspace root directory that belongs to a tenant.
 * <p>
 * Implementations typically query a registry or database. The returned path must be the absolute
 * path of the tenant root directory. When the tenant is not registered, return {@link Optional#empty()}
 * so that the caller falls back to the namespace-based default layout. Every returned path is
 * validated against the configured whitelist and workspace boundary before it is used.
 *
 * @since 0.1.17
 */
@FunctionalInterface
public interface TenantWorkspaceLocator {
    /**
     * Locate the workspace root directory of the tenant described by the context.
     *
     * @param ctx the current tenant context
     * @return the absolute tenant root path, or {@link Optional#empty()} when the tenant is not registered
     * @since 0.1.17
     */
    Optional<Path> locate(TenantContext ctx);
}
