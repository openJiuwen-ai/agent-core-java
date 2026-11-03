/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

import java.util.regex.Pattern;

/**
 * Thread-local holder for the current {@link TenantContext}, inherited by child threads.
 *
 * @since 0.1.7
 */
public final class TenantContextHolder {
    private static final InheritableThreadLocal<TenantContext> CURRENT_TENANT = new InheritableThreadLocal<>();
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]+");

    /**
     * Get the tenant context bound to the current thread.
     *
     * @return the current tenant context, or null if none is bound
     * @since 0.1.7
     */
    public static TenantContext getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    /**
     * Bind the given tenant context to the current thread, validating its tenant id.
     *
     * @param ctx the tenant context to bind, may be null to clear
     * @throws IllegalArgumentException if the tenant id contains characters outside [a-zA-Z0-9_-]
     * @since 0.1.7
     */
    public static void setCurrentTenant(TenantContext ctx) {
        if (ctx != null && ctx.isTenantAware()) {
            String tid = ctx.getTenantId();
            if (!TENANT_ID_PATTERN.matcher(tid).matches()) {
                throw new IllegalArgumentException(
                    "tenantId must only contain [a-zA-Z0-9_-], got: " + tid);
            }
        }
        CURRENT_TENANT.set(ctx);
    }

    /**
     * Remove the tenant context bound to the current thread.
     *
     * @since 0.1.7
     */
    public static void clearCurrentTenant() {
        CURRENT_TENANT.remove();
    }
}
