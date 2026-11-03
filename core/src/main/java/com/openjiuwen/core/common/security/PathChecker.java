/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mirrors Python's {@code PathChecker} in
 * {@code openjiuwen/core/common/security/path_checker.py}.
 */
public final class PathChecker {

    private static final Object LOCK = new Object();
    private static final List<String> FALLBACK_SENSITIVE_PATHS = List.of(
            "/etc/passwd",
            "/etc/shadow",
            "/etc/hosts",
            "/etc/hostname",
            "/etc/ssh/",
            "/proc/",
            "/sys/",
            "/dev/",
            "C:\\Windows\\System32\\",
            "C:\\Windows\\SysWOW64\\",
            "C:\\Windows\\System\\"
    );

    private static volatile PathChecker instance;

    private final Set<String> sensitivePaths = new HashSet<>();

    private PathChecker() {
        loadConfig();
    }

    public static PathChecker getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new PathChecker();
                }
            }
        }
        return instance;
    }

    public static boolean isSensitivePath(String path) {
        return getInstance().checkSensitive(path);
    }

    public static boolean isSensitivePath(Path path) {
        return getInstance().checkSensitive(path);
    }

    static void resetForTests() {
        synchronized (LOCK) {
            instance = null;
        }
    }

    private void loadConfig() {
        sensitivePaths.clear();
        List<String> configuredPaths;
        try {
            configuredPaths = UserConfig.getSensitivePaths();
        } catch (RuntimeException exception) {
            configuredPaths = FALLBACK_SENSITIVE_PATHS;
        }
        for (String rawPath : configuredPaths) {
            if (rawPath == null) {
                continue;
            }
            String trimmed = rawPath.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                sensitivePaths.add(normalize(trimmed));
            } catch (InvalidPathException | SecurityException exception) {
                sensitivePaths.add(trimmed);
            }
        }
    }

    public boolean checkSensitive(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        try {
            return isNormalizedPathSensitive(normalize(path));
        } catch (InvalidPathException | SecurityException exception) {
            return true;
        }
    }

    public boolean checkSensitive(Path path) {
        if (path == null) {
            return false;
        }
        try {
            return isNormalizedPathSensitive(normalize(path.toString()));
        } catch (InvalidPathException | SecurityException exception) {
            return true;
        }
    }

    private boolean isNormalizedPathSensitive(String normalizedPath) {
        for (String sensitivePath : sensitivePaths) {
            if (normalizedPath.startsWith(sensitivePath)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String rawPath) {
        return Path.of(expandUser(rawPath)).toAbsolutePath().normalize().toString();
    }

    private static String expandUser(String rawPath) {
        if (rawPath.startsWith("~")) {
            return System.getProperty("user.home") + rawPath.substring(1);
        }
        return rawPath;
    }
}
