/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Path checker — singleton that determines whether a file path is sensitive.
 * <p>
 * Thread-safe singleton using double-checked locking.
 * 
 * @since 0.1.7
 */
public final class PathChecker {
    private static volatile PathChecker instance;

    /**
     * HashSet<>.
     * 
     * @since 0.1.7
     */
    private final Set<String> sensitivePaths = new HashSet<>();

    /**
     * PathChecker.
     * 
     * @since 0.1.7
     */
    private PathChecker() {
        loadConfig();
    }

    /**
     * Get or create the singleton instance.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static PathChecker getInstance() {
        if (instance == null) {
            synchronized (PathChecker.class) {
                if (instance == null) {
                    instance = new PathChecker();
                }
            }
        }
        return instance;
    }

    /**
     * loadConfig.
     * 
     * @since 0.1.7
     */
    private void loadConfig() {
        sensitivePaths.clear();
        List<String> paths;
        try {
            paths = UserConfig.getSensitivePaths();
        } catch (Exception e) {
            paths = UserConfig.DEFAULT_SENSITIVE_PATHS;
        }
        for (String p : paths) {
            if (p == null || p.isBlank()) {
                continue;
            }
            try {
                String normalized = Path.of(p.trim()).toAbsolutePath().normalize().toString();
                sensitivePaths.add(normalized);
            } catch (Exception e) {
                sensitivePaths.add(p.trim());
            }
        }
    }

    /**
     * Check if a path is sensitive.
     * 
     * @param path path string to check
     * @return true if path starts with any configured sensitive path
     * @since 0.1.7
     */
    public boolean checkSensitive(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        try {
            String normalized = Path.of(path).toAbsolutePath().normalize().toString();
            for (String sensitive : sensitivePaths) {
                if (normalized.startsWith(sensitive)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return true; // Fail-isClosed
        }
    }

    /**
     * Convenience static method.
     * 
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    public static boolean isSensitivePath(String path) {
        return getInstance().checkSensitive(path);
    }
}
