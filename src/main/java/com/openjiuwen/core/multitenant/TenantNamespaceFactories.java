package com.openjiuwen.core.multitenant;

public final class TenantNamespaceFactories {

    /**
     * KV 存储命名空间工厂：产出 {@code tenantId:rawKey}，无租户时原样返回 rawKey。
     */
    public static final TenantNamespaceFactory KV_STORE_DEFAULT =
        (ctx, rawKey) -> {
            if (ctx != null && ctx.isTenantAware()) {
                return ctx.safeTenantId() + ":" + rawKey;
            }
            return rawKey;
        };

    /**
     * 文件系统路径命名空间工厂：产出 {@code tenants/{tenantId}}。
     * <p>注意：rawKey 参数对 PATH_DEFAULT 无意义（路径命名空间仅由 tenantId 决定），
     * 保留 rawKey 参数仅为统一 {@link TenantNamespaceFactory} 接口签名。
     * 无租户时返回空字符串（表示使用 basePath 根目录）。
     */
    public static final TenantNamespaceFactory PATH_DEFAULT =
        (ctx, rawKey) -> {
            if (ctx != null && ctx.isTenantAware()) {
                return "tenants/" + ctx.safeTenantId();
            }
            return "";
        };

    private TenantNamespaceFactories() {}
}
