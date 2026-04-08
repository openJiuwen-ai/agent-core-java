/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * User configuration — singleton that reads security settings from a properties/ini file.
 * <p>
 * Thread-safe singleton with configurable path.
 */
public final class UserConfig {

    public static final List<String> DEFAULT_SENSITIVE_PATHS = List.of(
        "/etc/passwd", "/etc/shadow", "/etc/hosts", "/etc/hostname", "/etc/ssh/",
        "C:\\Windows\\System32\\", "C:\\Windows\\SysWOW64\\", "C:\\Windows\\System\\"
    );

    private static volatile UserConfig instance;
    private static volatile Path configPath;

    private volatile boolean sensitive;
    private volatile List<String> sensitivePaths;
    private final Properties properties;

    private UserConfig(Path path) {
        this.properties = new Properties();
        if (path != null && Files.isRegularFile(path)) {
            try (InputStream is = Files.newInputStream(path)) {
                properties.load(is);
            } catch (IOException ignored) {
                // Fall back to defaults
            }
        }
        this.sensitive = Boolean.parseBoolean(
            properties.getProperty("settings.is_sensitive", "true"));
    }

    /** Set config path — must be called before first access. */
    public static void setConfigPath(Path path) {
        if (instance != null) {
            throw new IllegalStateException("Config already initialized");
        }
        configPath = path.toAbsolutePath().normalize();
    }

    /** Get the singleton config instance. */
    public static UserConfig getConfig() {
        if (instance == null) {
            synchronized (UserConfig.class) {
                if (instance == null) {
                    instance = new UserConfig(configPath);
                }
            }
        }
        return instance;
    }

    /** Whether sensitivity checking is enabled. */
    public static boolean isSensitive() {
        String envValue = System.getenv("IS_SENSITIVE");
        if ("false".equalsIgnoreCase(envValue)) {
            return false;
        }
        return getConfig().sensitive;
    }

    /** Get the list of sensitive paths. */
    public static List<String> getSensitivePaths() {
        return getConfig().getSensitivePathsList();
    }

    /**
     * Set the is_sensitive flag at runtime.
     *
     * @param isSensitive whether sensitivity checking should be enabled
     */
    public static void setSensitive(boolean isSensitive) {
        getConfig().sensitive = isSensitive;
    }

    /**
     * Get the resolved list of sensitive paths (lazy-initialized).
     *
     * @return an immutable copy of the sensitive paths
     */
    public List<String> getSensitivePathsList() {
        if (sensitivePaths == null) {
            synchronized (this) {
                if (sensitivePaths == null) {
                    String raw = properties.getProperty("settings.sensitive_paths", "");
                    if (!raw.isBlank()) {
                        List<String> list = new ArrayList<>();
                        for (String p : raw.split(",")) {
                            String trimmed = p.trim();
                            if (!trimmed.isEmpty()) {
                                list.add(trimmed);
                            }
                        }
                        sensitivePaths = List.copyOf(list);
                    } else {
                        sensitivePaths = DEFAULT_SENSITIVE_PATHS;
                    }
                }
            }
        }
        return sensitivePaths;
    }

    /** Reset singleton — primarily for testing. */
    public static synchronized void reset() {
        instance = null;
        configPath = null;
    }
}
