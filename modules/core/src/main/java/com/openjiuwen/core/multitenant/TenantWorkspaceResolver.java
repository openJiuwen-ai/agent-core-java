/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

import com.openjiuwen.core.common.exception.FrameworkError;
import com.openjiuwen.core.common.exception.StatusCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves tenant-scoped workspace paths under a shared base directory.
 *
 * @since 0.1.7
 */
public class TenantWorkspaceResolver {
    private final String baseWorkspacePath;
    private final ConcurrentHashMap<String, Path> initializedTenants = new ConcurrentHashMap<>();
    private final TenantNamespaceFactory namespaceFactory;

    public TenantWorkspaceResolver(String baseWorkspacePath) {
        this(baseWorkspacePath, null);
    }

    public TenantWorkspaceResolver(String baseWorkspacePath, TenantNamespaceFactory namespaceFactory) {
        this.baseWorkspacePath = baseWorkspacePath;
        this.namespaceFactory = namespaceFactory != null ? namespaceFactory : TenantNamespaceFactories.PATH_DEFAULT;
    }

    public TenantNamespaceFactory getNamespaceFactory() {
        return namespaceFactory;
    }

    private String tenantNamespace(TenantContext ctx) {
        return namespaceFactory.namespace(ctx, "tenants");
    }

    /**
     * Resolve the tenant root directory, falling back to the base path when no tenant is bound.
     *
     * @param ctx the tenant context
     * @return the absolute normalized tenant root path
     * @since 0.1.7
     */
    public Path resolveTenantRoot(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return Path.of(baseWorkspacePath).toAbsolutePath().normalize();
        }
        String ns = tenantNamespace(ctx);
        return Path.of(baseWorkspacePath, ns).toAbsolutePath().normalize();
    }

    /**
     * Resolve the workspace root for the given tenant context.
     *
     * @param ctx the tenant context
     * @return the absolute normalized workspace root path
     * @since 0.1.7
     */
    public Path resolveWorkspaceRoot(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return Path.of(baseWorkspacePath).toAbsolutePath().normalize();
        }
        return resolveTenantRoot(ctx).toAbsolutePath().normalize();
    }

    /**
     * Resolve the skills directory for the given tenant context.
     *
     * @param ctx the tenant context
     * @return the absolute normalized skills path
     * @since 0.1.7
     */
    public Path resolveSkillRoot(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return Path.of(baseWorkspacePath, "skills").toAbsolutePath().normalize();
        }
        return resolveTenantRoot(ctx).resolve("skills").toAbsolutePath().normalize();
    }

    /**
     * Resolve the tmp directory for the given tenant context.
     *
     * @param ctx the tenant context
     * @return the absolute normalized tmp path
     * @since 0.1.7
     */
    public Path resolveTempDir(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return Path.of(baseWorkspacePath, "tmp").toAbsolutePath().normalize();
        }
        return resolveTenantRoot(ctx).resolve("tmp").toAbsolutePath().normalize();
    }

    /**
     * Resolve the checkpoints directory for the given tenant context.
     *
     * @param ctx the tenant context
     * @return the absolute normalized checkpoints path
     * @since 0.1.7
     */
    public Path resolveCheckpointDir(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return Path.of(baseWorkspacePath, "checkpoints").toAbsolutePath().normalize();
        }
        return resolveTenantRoot(ctx).resolve("checkpoints").toAbsolutePath().normalize();
    }

    /**
     * Resolve the team memory directory for the given tenant context.
     *
     * @param ctx the tenant context
     * @return the absolute normalized team memory path
     * @since 0.1.7
     */
    public Path resolveTeamMemoryDir(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return Path.of(baseWorkspacePath, "team_memory").toAbsolutePath().normalize();
        }
        return resolveTenantRoot(ctx).resolve("team_memory").toAbsolutePath().normalize();
    }

    /**
     * Resolve the todo directory for the given tenant context.
     *
     * @param ctx the tenant context
     * @return the absolute normalized todo path
     * @since 0.1.7
     */
    public Path resolveTodoDir(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return Path.of(baseWorkspacePath, "todo").toAbsolutePath().normalize();
        }
        return resolveTenantRoot(ctx).resolve("todo").toAbsolutePath().normalize();
    }

    /**
     * Initialize the on-disk directory structure for the tenant, returning the tenant root.
     *
     * @param ctx the tenant context
     * @return the absolute normalized tenant root path
     * @since 0.1.7
     */
    public Path initializeTenantSpace(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return Path.of(baseWorkspacePath).toAbsolutePath().normalize();
        }
        String tid = ctx.safeTenantId();
        Path cached = initializedTenants.get(tid);
        if (cached != null) {
            return cached;
        }
        // Two-phase init: create the on-disk structure OUTSIDE the map lock so that
        // concurrent tenant initializations never serialize on disk I/O inside the
        // ConcurrentHashMap bin lock. createDirectories is idempotent, so duplicate
        // concurrent initialization is safe.
        Path tenantRoot = resolveTenantRoot(ctx);
        try {
            Files.createDirectories(tenantRoot);
            Files.createDirectories(tenantRoot.resolve("skills"));
            Files.createDirectories(tenantRoot.resolve("tmp"));
            Files.createDirectories(tenantRoot.resolve("checkpoints"));
            Files.createDirectories(tenantRoot.resolve("team_memory"));
            Files.createDirectories(tenantRoot.resolve("todo"));
            Files.createDirectories(tenantRoot.resolve(".overlay"));
        } catch (IOException e) {
            throw new FrameworkError(StatusCode.ERROR,
                "Failed to initialize tenant space: " + tid, null, e, null);
        }
        Path previous = initializedTenants.putIfAbsent(tid, tenantRoot);
        return previous != null ? previous : tenantRoot;
    }

    /**
     * Check whether the requested path is contained within the tenant boundary.
     *
     * @param requestedPath the path to check
     * @param ctx the tenant context
     * @return true if the path is within the tenant boundary or no tenant is bound
     * @since 0.1.7
     */
    public boolean isPathWithinTenant(Path requestedPath, TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return true;
        }
        Path tenantRoot = resolveTenantRoot(ctx);
        Path normalized = requestedPath.toAbsolutePath().normalize();
        return normalized.startsWith(tenantRoot);
    }
}
