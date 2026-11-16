/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable policy that decides where a tenant workspace lives.
 * <p>
 * Resolution priority is: an explicit {@link TenantContext#getWorkspaceRoot()}, then a
 * {@link TenantWorkspaceLocator} lookup, then the namespace factory relative to the base workspace.
 * Non-default paths must stay inside {@code allowedTenantRoots} when it is configured, otherwise they
 * must stay inside the base workspace.
 *
 * @since 0.1.17
 */
public final class TenantWorkspacePolicy {
    private final String baseWorkspacePath;
    private final TenantNamespaceFactory namespaceFactory;
    private final TenantWorkspaceLocator locator;
    private final List<String> allowedTenantRoots;
    private final Path basePath;
    private final Path baseRealPath;
    private final List<Path> effectiveRealAllowedRoots;

    private TenantWorkspacePolicy(Builder builder) {
        this.baseWorkspacePath = Objects.requireNonNull(builder.baseWorkspacePath,
            "baseWorkspacePath must not be null");
        this.namespaceFactory = builder.namespaceFactory != null
            ? builder.namespaceFactory : TenantNamespaceFactories.PATH_DEFAULT;
        this.locator = builder.locator;
        this.allowedTenantRoots = builder.allowedTenantRoots == null
            ? List.of() : List.copyOf(builder.allowedTenantRoots);
        this.basePath = Path.of(baseWorkspacePath).toAbsolutePath().normalize();
        this.baseRealPath = resolveRealPath(basePath);
        this.effectiveRealAllowedRoots = resolveRealRoots(buildAllowedRoots());
    }

    /**
     * Create a policy builder.
     *
     * @return a new builder
     * @since 0.1.17
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Resolve the tenant root directory according to the configured priority.
     *
     * @param ctx the tenant context
     * @return the validated absolute tenant root; the base path when no tenant is bound
     * @throws SecurityException when a resolved path escapes every allowed tenant root
     * @since 0.1.17
     */
    public Path resolveTenantRoot(TenantContext ctx) {
        if (ctx == null || !ctx.isTenantAware()) {
            return basePath;
        }
        String workspaceRoot = ctx.getWorkspaceRoot();
        if (workspaceRoot != null && !workspaceRoot.isBlank()) {
            return validateCandidate(Path.of(workspaceRoot));
        }
        if (locator != null) {
            Optional<Path> located = locator.locate(ctx);
            if (located.isPresent()) {
                return validateCandidate(located.get());
            }
        }
        return validateCandidate(basePath.resolve(namespaceFactory.namespace(ctx, "tenants")));
    }

    /**
     * Resolve the tenant namespace relative to the base workspace for tiered storage.
     *
     * @param ctx the tenant context
     * @return the relative namespace using {@code /} separators; empty when no tenant is bound
     * @throws IllegalArgumentException when the tenant root is outside the base workspace
     * @since 0.1.17
     */
    public String resolveNamespace(TenantContext ctx) {
        if (ctx == null || !ctx.isTenantAware()) {
            return "";
        }
        Path tenantRoot = resolveTenantRoot(ctx);
        Path relative = relativeTo(tenantRoot, basePath);
        if (relative == null) {
            relative = relativeTo(resolveRealPath(tenantRoot), baseRealPath);
        }
        if (relative == null) {
            throw new IllegalArgumentException(
                "Tiered workspace requires the tenant directory to be under the base workspace: " + tenantRoot);
        }
        return relative.toString().replace('\\', '/');
    }

    private static Path relativeTo(Path path, Path root) {
        return path.startsWith(root) ? root.relativize(path) : null;
    }

    /**
     * Return the configured base workspace path.
     *
     * @return the base workspace path as configured
     * @since 0.1.17
     */
    public String getBaseWorkspacePath() {
        return baseWorkspacePath;
    }

    /**
     * Return the configured tenant namespace factory.
     *
     * @return the namespace factory, never null
     * @since 0.1.17
     */
    public TenantNamespaceFactory getNamespaceFactory() {
        return namespaceFactory;
    }

    private List<Path> buildAllowedRoots() {
        if (allowedTenantRoots.isEmpty()) {
            return List.of(basePath);
        }
        List<Path> roots = new ArrayList<>(allowedTenantRoots.size());
        for (String root : allowedTenantRoots) {
            roots.add(Path.of(root).toAbsolutePath().normalize());
        }
        return List.copyOf(roots);
    }

    private Path validateCandidate(Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        Path realPath = resolveRealPath(normalized);
        for (Path root : effectiveRealAllowedRoots) {
            if (realPath.startsWith(root)) {
                return normalized;
            }
        }
        throw new SecurityException("Tenant workspace path is outside the allowed roots: "
            + normalized + " (real path: " + realPath + ")");
    }

    private static List<Path> resolveRealRoots(List<Path> roots) {
        List<Path> realRoots = new ArrayList<>(roots.size());
        for (Path root : roots) {
            realRoots.add(resolveRealPath(root));
        }
        return List.copyOf(realRoots);
    }

    /**
     * Resolve the real path of a path whose leaf (or several trailing segments) may not exist yet.
     * <p>
     * The nearest existing ancestor is resolved with {@link Path#toRealPath()} so symbolic links or
     * junctions are followed, then the missing segments are appended. This keeps a base workspace
     * that is itself a link usable while still rejecting candidates that escape through a link.
     *
     * @param path the path to resolve
     * @return the real path of the path
     * @throws SecurityException when an existing link cannot be resolved
     */
    private static Path resolveRealPath(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        List<Path> missingSegments = new ArrayList<>();
        Path existing = absolute;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            missingSegments.add(0, existing.getFileName());
            existing = existing.getParent();
        }
        if (existing == null) {
            return absolute;
        }
        Path realPath;
        try {
            realPath = existing.toRealPath();
        } catch (IOException e) {
            throw new SecurityException("Failed to resolve tenant workspace real path: " + existing, e);
        }
        for (Path segment : missingSegments) {
            realPath = realPath.resolve(segment);
        }
        return realPath.normalize();
    }

    /**
     * Builder for {@link TenantWorkspacePolicy}.
     *
     * @since 0.1.17
     */
    public static final class Builder {
        private String baseWorkspacePath;
        private TenantNamespaceFactory namespaceFactory;
        private TenantWorkspaceLocator locator;
        private List<String> allowedTenantRoots;

        private Builder() {
        }

        /**
         * Set the base workspace path.
         *
         * @param path the base workspace path
         * @return this builder
         * @since 0.1.17
         */
        public Builder baseWorkspacePath(String path) {
            this.baseWorkspacePath = path;
            return this;
        }

        /**
         * Set the namespace factory used by the default layout.
         *
         * @param factory the namespace factory, or null for the default path factory
         * @return this builder
         * @since 0.1.17
         */
        public Builder namespaceFactory(TenantNamespaceFactory factory) {
            this.namespaceFactory = factory;
            return this;
        }

        /**
         * Set the locator used to look up a tenant workspace root.
         *
         * @param workspaceLocator the locator, or null to skip lookup
         * @return this builder
         * @since 0.1.17
         */
        public Builder locator(TenantWorkspaceLocator workspaceLocator) {
            this.locator = workspaceLocator;
            return this;
        }

        /**
         * Set the roots that tenant workspaces are allowed to live under.
         *
         * @param roots the allowed roots; null or empty restricts tenants to the base workspace
         * @return this builder
         * @since 0.1.17
         */
        public Builder allowedTenantRoots(List<String> roots) {
            this.allowedTenantRoots = roots;
            return this;
        }

        /**
         * Build the policy.
         *
         * @return the immutable policy
         * @since 0.1.17
         */
        public TenantWorkspacePolicy build() {
            return new TenantWorkspacePolicy(this);
        }
    }
}
