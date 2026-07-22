package com.openjiuwen.core.multitenant;

public class TenantKVStoreKeyResolver {

    private static TenantNamespaceFactory globalNamespaceFactory = TenantNamespaceFactories.KV_STORE_DEFAULT;

    public static void setGlobalNamespaceFactory(TenantNamespaceFactory factory) {
        globalNamespaceFactory = factory != null ? factory : TenantNamespaceFactories.KV_STORE_DEFAULT;
    }

    public static TenantNamespaceFactory getGlobalNamespaceFactory() {
        return globalNamespaceFactory;
    }

    public static String resolveKey(String originalKey) {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        return globalNamespaceFactory.namespace(ctx, originalKey);
    }

    public static String resolveKeyWithFactory(TenantNamespaceFactory factory, String originalKey) {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        TenantNamespaceFactory nsFactory = factory != null ? factory : TenantNamespaceFactories.KV_STORE_DEFAULT;
        return nsFactory.namespace(ctx, originalKey);
    }

    public static String resolvePrefix(String originalPrefix) {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        return globalNamespaceFactory.namespace(ctx, originalPrefix);
    }

    public static String resolvePrefixWithFactory(TenantNamespaceFactory factory, String originalPrefix) {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        TenantNamespaceFactory nsFactory = factory != null ? factory : TenantNamespaceFactories.KV_STORE_DEFAULT;
        return nsFactory.namespace(ctx, originalPrefix);
    }
}
