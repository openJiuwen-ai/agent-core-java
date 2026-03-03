// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 敏感路径检查器（单例模式）
 *
 * <p>用于检查给定路径是否为敏感路径。</p>
 */
public final class PathChecker {

    private static volatile PathChecker instance;
    private static final Object LOCK = new Object();

    private final Set<String> sensitivePaths;
    private volatile boolean initialized = false;

    private PathChecker() {
        this.sensitivePaths = new HashSet<>();
        loadConfig();
        initialized = true;
    }

    /**
     * 获取PathChecker单例实例
     *
     * @return PathChecker实例
     */
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

    /**
     * 检查路径是否为敏感路径
     *
     * @param path 要检查的路径（可以是String、Path或null）
     * @return 如果是敏感路径返回true，否则返回false
     */
    public boolean isSensitivePath(Object path) {
        if (path == null) {
            return false;
        }

        String pathStr;
        if (path instanceof String) {
            pathStr = (String) path;
        } else if (path instanceof Path) {
            pathStr = path.toString();
        } else {
            return false;
        }

        try {
            String normalizedPath = Paths.get(pathStr).toAbsolutePath().normalize().toString();

            for (String sensitivePath : sensitivePaths) {
                if (normalizedPath.startsWith(sensitivePath)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 重新加载配置
     */
    public void reloadConfig() {
        synchronized (LOCK) {
            sensitivePaths.clear();
            loadConfig();
        }
    }

    /**
     * 获取敏感路径列表（副本）
     *
     * @return 敏感路径列表
     */
    public List<String> getSensitivePaths() {
        synchronized (LOCK) {
            return List.copyOf(sensitivePaths);
        }
    }

    /**
     * 加载配置
     */
    private void loadConfig() {
        sensitivePaths.clear();

        List<String> pathsFromConfig;
        try {
            pathsFromConfig = UserConfig.getInstance().getSensitivePaths();
        } catch (Exception e) {
            // 使用默认的敏感路径列表
            pathsFromConfig = getDefaultSensitivePaths();
        }

        for (String path : pathsFromConfig) {
            if (path == null || path.isEmpty()) {
                continue;
            }

            try {
                String normalizedPath = Paths.get(path.trim()).toRealPath().normalize().toString();
                sensitivePaths.add(normalizedPath);
            } catch (Exception e) {
                // 如果无法规范化，使用原始路径
                sensitivePaths.add(path.trim());
            }
        }
    }

    /**
     * 获取默认的敏感路径列表
     *
     * @return 默认敏感路径列表
     */
    private List<String> getDefaultSensitivePaths() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            return List.of(
                "C:\\Windows\\System32\\",
                "C:\\Windows\\SysWOW64\\",
                "C:\\Windows\\System\\",
                "C:\\Program Files\\",
                "C:\\Program Files (x86)\\",
                "C:\\Users\\All Users\\",
                "C:\\Windows\\System32\\config\\"
            );
        } else {
            return List.of(
                "/etc/passwd",
                "/etc/shadow",
                "/etc/hosts",
                "/etc/hostname",
                "/etc/ssh/",
                "/proc/",
                "/sys/",
                "/dev/",
                "/etc/systemd/",
                "/var/log/"
            );
        }
    }
}