package com.openjiuwen.core.multitenant;

public final class TenantContextHolder {
    private static final InheritableThreadLocal<TenantContext> CURRENT_TENANT = new InheritableThreadLocal<>();

    public static TenantContext getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentTenant(TenantContext ctx) {
        if (ctx != null && ctx.isTenantAware()) {
            String tid = ctx.getTenantId();
            if (!tid.matches("[a-zA-Z0-9_-]+")) {
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
