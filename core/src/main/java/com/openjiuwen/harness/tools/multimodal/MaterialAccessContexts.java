/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.multimodal;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.multimodal.MaterialAccessConfig;
import com.openjiuwen.core.foundation.llm.multimodal.MaterialAccessContext;
import com.openjiuwen.core.sysop.Cwd;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds {@link MaterialAccessContext} instances from agent configuration.
 *
 * <p>Configured roots and asset roots are canonicalized strictly; when no
 * roots are configured the controlled working directories of the execution
 * environment ({@link Cwd} workspace, cwd, project root and team workspace)
 * become the default allowed roots, and the default asset root is a
 * workspace-bound directory instead of the global temporary directory.</p>
 *
 * @since 0.1.17
 */
public final class MaterialAccessContexts {
    private static final String DEFAULT_ASSET_DIR_NAME = ".material-assets";

    private MaterialAccessContexts() {
    }

    /**
     * Resolve the per-request access and budget context.
     *
     * @param config agent-level material access configuration; may be {@code null}
     * @return context with canonicalized roots and effective budgets, never {@code null}
     * @throws com.openjiuwen.core.common.exception.BaseError when a configured
     *         path cannot be resolved
     */
    public static MaterialAccessContext fromConfig(MaterialAccessConfig config) {
        List<String> allowedRoots = configuredRoots(config);
        if (allowedRoots.isEmpty()) {
            allowedRoots = workspaceRoots();
        }
        long maxImageBytes = positiveOrDefault(config == null ? null : config.getMaxImageBytes(),
                MaterialAccessContext.DEFAULT_MAX_IMAGE_BYTES);
        long maxTotalImageBytes = positiveOrDefault(config == null ? null : config.getMaxTotalImageBytes(),
                MaterialAccessContext.DEFAULT_MAX_TOTAL_IMAGE_BYTES);
        long readTimeoutMillis = nonNegativeOrDefault(config == null ? null : config.getReadTimeoutMillis(),
                MaterialAccessContext.DEFAULT_READ_TIMEOUT_MILLIS);
        return new MaterialAccessContext(allowedRoots, resolveAssetRoot(config),
                maxImageBytes, maxTotalImageBytes, readTimeoutMillis, 0L);
    }

    private static List<String> configuredRoots(MaterialAccessConfig config) {
        if (config == null || config.getAllowedRoots() == null) {
            return List.of();
        }
        List<String> roots = new ArrayList<>();
        for (String root : config.getAllowedRoots()) {
            if (root == null || root.isBlank()) {
                continue;
            }
            String canonical = canonicalizeStrictly(root.trim());
            if (!roots.contains(canonical)) {
                roots.add(canonical);
            }
        }
        return roots;
    }

    private static List<String> workspaceRoots() {
        List<String> roots = new ArrayList<>();
        addBestEffortRoot(roots, Cwd.getWorkspace());
        addBestEffortRoot(roots, Cwd.getCwd());
        addBestEffortRoot(roots, Cwd.getProjectRoot());
        addBestEffortRoot(roots, Cwd.getTeamWorkspace());
        return roots;
    }

    private static void addBestEffortRoot(List<String> roots, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        canonicalizeBestEffort(candidate.trim()).ifPresent(canonical -> {
            if (!roots.contains(canonical)) {
                roots.add(canonical);
            }
        });
    }

    private static String resolveAssetRoot(MaterialAccessConfig config) {
        if (config != null && config.getAssetRoot() != null && !config.getAssetRoot().isBlank()) {
            return canonicalizeStrictly(config.getAssetRoot().trim());
        }
        String base = Cwd.getWorkspace();
        if (base == null || base.isBlank()) {
            base = Cwd.getCwd();
        }
        String defaultRoot = Path.of(base, DEFAULT_ASSET_DIR_NAME).toString();
        return canonicalizeBestEffort(defaultRoot).orElse(defaultRoot);
    }

    private static String canonicalizeStrictly(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException | InvalidPathException exception) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("error_msg", "materialAccess path cannot be resolved: " + path);
            throw ErrorHelper.buildError(StatusCode.COMMON_USER_CONFIG_PROCESS_ERROR,
                    "materialAccess path cannot be resolved: " + path, null, exception, params);
        }
    }

    private static Optional<String> canonicalizeBestEffort(String path) {
        try {
            return Optional.of(new File(path).getCanonicalPath());
        } catch (IOException | InvalidPathException exception) {
            return normalizeFallback(path);
        }
    }

    private static Optional<String> normalizeFallback(String path) {
        try {
            return Optional.of(Path.of(path).toAbsolutePath().normalize().toString());
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }

    private static long positiveOrDefault(Long value, long fallback) {
        return value == null || value <= 0L ? fallback : value.longValue();
    }

    private static long nonNegativeOrDefault(Long value, long fallback) {
        return value == null || value < 0L ? fallback : value.longValue();
    }
}
