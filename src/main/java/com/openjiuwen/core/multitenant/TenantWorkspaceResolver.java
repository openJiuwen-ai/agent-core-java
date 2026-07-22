package com.openjiuwen.core.multitenant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

public class TenantWorkspaceResolver {
    private final String baseWorkspacePath;
    private final ConcurrentHashMap<String, Path> initializedTenants = new ConcurrentHashMap<>();
    private final TenantNamespaceFactory namespaceFactory;

    public TenantWorkspaceResolver(String baseWorkspacePath) {
        this.baseWorkspacePath = baseWorkspacePath;
        this.namespaceFactory = TenantNamespaceFactories.PATH_DEFAULT;
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

    public Path resolveTenantRoot(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return Path.of(baseWorkspacePath).toAbsolutePath().normalize();
        }
        String ns = tenantNamespace(ctx);
        return Path.of(baseWorkspacePath, ns).toAbsolutePath().normalize();
    }

    public Path resolveWorkspaceRoot(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return Path.of(baseWorkspacePath).toAbsolutePath().normalize();
        }
        return resolveTenantRoot(ctx).toAbsolutePath().normalize();
    }

    public Path resolveSkillRoot(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return Path.of(baseWorkspacePath, "skills").toAbsolutePath().normalize();
        }
        return resolveTenantRoot(ctx).resolve("skills").toAbsolutePath().normalize();
    }

    public Path resolveTempDir(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return Path.of(baseWorkspacePath, "tmp").toAbsolutePath().normalize();
        }
        return resolveTenantRoot(ctx).resolve("tmp").toAbsolutePath().normalize();
    }

    public Path resolveCheckpointDir(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return Path.of(baseWorkspacePath, "checkpoints").toAbsolutePath().normalize();
        }
        return resolveTenantRoot(ctx).resolve("checkpoints").toAbsolutePath().normalize();
    }

    public Path resolveTeamMemoryDir(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return Path.of(baseWorkspacePath, "team_memory").toAbsolutePath().normalize();
        }
        return resolveTenantRoot(ctx).resolve("team_memory").toAbsolutePath().normalize();
    }

    public Path resolveTodoDir(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return Path.of(baseWorkspacePath, "todo").toAbsolutePath().normalize();
        }
        return resolveTenantRoot(ctx).resolve("todo").toAbsolutePath().normalize();
    }

    public Path initializeTenantSpace(TenantContext ctx) {
        if (!ctx.isTenantAware()) {
            return Path.of(baseWorkspacePath).toAbsolutePath().normalize();
        }
        return initializedTenants.computeIfAbsent(ctx.safeTenantId(), tid -> {
            Path tenantRoot = resolveTenantRoot(ctx);
            try {
                Files.createDirectories(tenantRoot);
                Files.createDirectories(tenantRoot.resolve("skills"));
                Files.createDirectories(tenantRoot.resolve("tmp"));
                Files.createDirectories(tenantRoot.resolve("checkpoints"));
                Files.createDirectories(tenantRoot.resolve("team_memory"));
                Files.createDirectories(tenantRoot.resolve("todo"));
                Files.createDirectories(tenantRoot.resolve(".overlay"));
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize tenant space: " + tid, e);
            }
            return tenantRoot;
        });
    }

    public boolean isPathWithinTenant(Path requestedPath, TenantContext ctx) {
        if (!ctx.isTenantAware()) return true;
        Path tenantRoot = resolveTenantRoot(ctx);
        Path normalized = requestedPath.toAbsolutePath().normalize();
        return normalized.startsWith(tenantRoot);
    }
}
