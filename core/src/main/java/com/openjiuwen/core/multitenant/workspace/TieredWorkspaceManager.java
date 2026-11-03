/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant.workspace;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantNamespaceFactory;
import com.openjiuwen.core.multitenant.TenantNamespaceFactories;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages tiered workspace resolution across a primary local store and remote stores.
 *
 * @since 0.1.7
 */
public class TieredWorkspaceManager {
    /** Local storage tier name. */
    public static final String TIER_LOCAL = "local";

    /** OBS storage tier name. */
    public static final String TIER_OBS = "obs";

    /** HDFS storage tier name. */
    public static final String TIER_HDFS = "hdfs";

    /** S3 storage tier name. */
    public static final String TIER_S3 = "s3";

    private final WorkspaceStore primaryStore;
    private final List<WorkspaceStore> secondaryStores;
    private final TenantNamespaceFactory namespaceFactory;
    private final ConcurrentHashMap<String, WorkspaceResolution> resolvedPaths = new ConcurrentHashMap<>();

    public TieredWorkspaceManager(WorkspaceStore primaryStore, List<WorkspaceStore> secondaryStores) {
        this(primaryStore, secondaryStores, TenantNamespaceFactories.PATH_DEFAULT);
    }

    public TieredWorkspaceManager(WorkspaceStore primaryStore, List<WorkspaceStore> secondaryStores,
                                  TenantNamespaceFactory namespaceFactory) {
        this.primaryStore = primaryStore;
        this.secondaryStores = secondaryStores != null ? secondaryStores : List.of();
        this.namespaceFactory = namespaceFactory != null ? namespaceFactory : TenantNamespaceFactories.PATH_DEFAULT;
    }

    public WorkspaceStore getPrimaryStore() {
        return primaryStore;
    }

    public List<WorkspaceStore> getSecondaryStores() {
        return secondaryStores;
    }

    public TenantNamespaceFactory getNamespaceFactory() {
        return namespaceFactory;
    }

    /**
     * Resolves the workspace paths for a tenant context, caching the result.
     *
     * @param ctx the tenant context
     * @param type the workspace type
     * @return the resolved workspace paths
     * @since 0.1.7
     */
    public WorkspaceResolution resolve(TenantContext ctx, WorkspaceType type) {
        if (!ctx.isTenantAware()) {
            return resolveDefault(type);
        }
        return resolvedPaths.computeIfAbsent(buildCacheKey(ctx, type), key -> {
            String ns = namespaceFactory.namespace(ctx, "tenants");
            String subDir = type.subDirectory();
            Path localPath = primaryStore.resolvePath(ns, subDir);
            Map<String, String> remotePaths = new java.util.LinkedHashMap<>();
            for (WorkspaceStore store : secondaryStores) {
                remotePaths.put(store.tierName(), store.resolvePath(ns, subDir).toString());
            }
            return new WorkspaceResolution(localPath, remotePaths, type);
        });
    }

    /**
     * Resolves default workspace paths outside any tenant namespace.
     *
     * @param type the workspace type
     * @return the resolved default workspace paths
     * @since 0.1.7
     */
    public WorkspaceResolution resolveDefault(WorkspaceType type) {
        Path localPath = primaryStore.resolveDefaultPath(type.subDirectory());
        Map<String, String> remotePaths = new java.util.LinkedHashMap<>();
        for (WorkspaceStore store : secondaryStores) {
            remotePaths.put(store.tierName(), store.resolveDefaultPath(type.subDirectory()).toString());
        }
        return new WorkspaceResolution(localPath, remotePaths, type);
    }

    /**
     * Initializes the tenant workspace directories across all configured stores.
     *
     * @param ctx the tenant context
     * @since 0.1.7
     */
    public void initializeTenantSpace(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return;
        }
        String ns = namespaceFactory.namespace(ctx, "tenants");
        primaryStore.createDirectories(ns);
        for (WorkspaceStore store : secondaryStores) {
            store.createDirectories(ns);
        }
    }

    private String buildCacheKey(TenantContext ctx, WorkspaceType type) {
        return ctx.safeTenantId() + ":" + type.name();
    }
}
