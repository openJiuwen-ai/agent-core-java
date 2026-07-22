package com.openjiuwen.core.multitenant;

import java.util.regex.Pattern;

public final class TenantContextHolder {
    private static final InheritableThreadLocal<TenantContext> CURRENT_TENANT = new InheritableThreadLocal<>();
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]+");

    public static TenantContext getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

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

    public static void clearCurrentTenant() {
        CURRENT_TENANT.remove();
    }
}
