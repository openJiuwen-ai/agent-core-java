/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

/**
 * Resolves tenant-scoped keys for the key-value store using a configurable namespace factory.
 *
 * @since 0.1.7
 */
public class TenantKVStoreKeyResolver {
    private static TenantNamespaceFactory globalNamespaceFactory = TenantNamespaceFactories.KV_STORE_DEFAULT;

    /**
     * Replace the global namespace factory used by the default resolvers.
     *
     * @param factory the factory to install, or null to reset to the default
     * @since 0.1.7
     */
    public static void setGlobalNamespaceFactory(TenantNamespaceFactory factory) {
        globalNamespaceFactory = factory != null ? factory : TenantNamespaceFactories.KV_STORE_DEFAULT;
    }

    /**
     * Return the global namespace factory currently in use.
     *
     * @return the global namespace factory
     * @since 0.1.7
     */
    public static TenantNamespaceFactory getGlobalNamespaceFactory() {
        return globalNamespaceFactory;
    }

    /**
     * Resolve a tenant-scoped key using the global namespace factory.
     *
     * @param originalKey the original key
     * @return the resolved key
     * @since 0.1.7
     */
    public static String resolveKey(String originalKey) {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        return globalNamespaceFactory.namespace(ctx, originalKey);
    }

    /**
     * Resolve a tenant-scoped key using the supplied namespace factory.
     *
     * @param factory the namespace factory to use, or null to fall back to the default
     * @param originalKey the original key
     * @return the resolved key
     * @since 0.1.7
     */
    public static String resolveKeyWithFactory(TenantNamespaceFactory factory, String originalKey) {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        TenantNamespaceFactory nsFactory = factory != null ? factory : TenantNamespaceFactories.KV_STORE_DEFAULT;
        return nsFactory.namespace(ctx, originalKey);
    }

    /**
     * Resolve a tenant-scoped prefix using the global namespace factory.
     *
     * @param originalPrefix the original prefix
     * @return the resolved prefix
     * @since 0.1.7
     */
    public static String resolvePrefix(String originalPrefix) {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        return globalNamespaceFactory.namespace(ctx, originalPrefix);
    }

    /**
     * Resolve a tenant-scoped prefix using the supplied namespace factory.
     *
     * @param factory the namespace factory to use, or null to fall back to the default
     * @param originalPrefix the original prefix
     * @return the resolved prefix
     * @since 0.1.7
     */
    public static String resolvePrefixWithFactory(TenantNamespaceFactory factory, String originalPrefix) {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        TenantNamespaceFactory nsFactory = factory != null ? factory : TenantNamespaceFactories.KV_STORE_DEFAULT;
        return nsFactory.namespace(ctx, originalPrefix);
    }
}
