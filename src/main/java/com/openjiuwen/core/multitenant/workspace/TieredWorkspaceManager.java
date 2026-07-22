package com.openjiuwen.core.multitenant.workspace;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantNamespaceFactory;
import com.openjiuwen.core.multitenant.TenantNamespaceFactories;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TieredWorkspaceManager {
    private final WorkspaceStore primaryStore;
    private final List<WorkspaceStore> secondaryStores;
    private final TenantNamespaceFactory namespaceFactory;
    private final ConcurrentHashMap<String, WorkspaceResolution> resolvedPaths = new ConcurrentHashMap<>();

    public static final String TIER_LOCAL = "local";
    public static final String TIER_OBS = "obs";
    public static final String TIER_HDFS = "hdfs";
    public static final String TIER_S3 = "s3";

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

    public WorkspaceResolution resolveDefault(WorkspaceType type) {
        Path localPath = primaryStore.resolveDefaultPath(type.subDirectory());
        Map<String, String> remotePaths = new java.util.LinkedHashMap<>();
        for (WorkspaceStore store : secondaryStores) {
            remotePaths.put(store.tierName(), store.resolveDefaultPath(type.subDirectory()).toString());
        }
        return new WorkspaceResolution(localPath, remotePaths, type);
    }

    public void initializeTenantSpace(TenantContext ctx) {
        if (!ctx.isTenantAware()) return;
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
