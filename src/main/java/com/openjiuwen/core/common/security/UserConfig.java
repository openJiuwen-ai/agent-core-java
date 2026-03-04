// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.security;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * 用户配置类（单例模式）
 *
 * <p>管理用户配置，特别是敏感路径配置。</p>
 */
public final class UserConfig {

    private static volatile UserConfig instance;
    private static volatile Path userConfigPath;
    private static final Object LOCK = new Object();

    private final Properties properties;
    private volatile boolean isSensitive;
    private volatile List<String> sensitivePaths;

    private static final String DEFAULT_SENSITIVE_PATH_STR =
        "/etc/passwd,/etc/shadow,/etc/hosts,/etc/hostname,/etc/ssh/," +
        "C:\\Windows\\System32\\,C:\\Windows\\SysWOW64\\,C:\\Windows\\System\\";

    private static final List<String> DEFAULT_SENSITIVE_PATHS = Arrays.asList(
        "/etc/passwd", "/etc/shadow", "/etc/hosts", "/etc/hostname", "/etc/ssh/",
        "C:\\Windows\\System32\\", "C:\\Windows\\SysWOW64\\", "C:\\Windows\\System\\"
    );

    /**
     * 私有构造函数
     *
     * @param configPath 配置文件路径
     */
    private UserConfig(Path configPath) {
        this.properties = new Properties();
        this.sensitivePaths = null;

        if (configPath != null && Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                properties.load(reader);
            } catch (IOException e) {
                // 使用默认配置
                loadDefaultConfig();
            }
        } else {
            loadDefaultConfig();
        }

        this.isSensitive = Boolean.parseBoolean(
            properties.getProperty("settings.is_sensitive", "true")
        );
    }

    /**
     * 加载默认配置
     */
    private void loadDefaultConfig() {
        properties.setProperty("settings.is_sensitive", "true");
        properties.setProperty("settings.sensitive_paths", DEFAULT_SENSITIVE_PATH_STR);
    }

    /**
     * 设置配置文件路径
     *
     * @param path 配置文件路径
     * @throws IllegalStateException 如果配置已初始化
     * @throws IllegalArgumentException 如果配置文件不在项目根目录下
     */
    public static void setConfigPath(Path path) {
        if (instance != null) {
            throw new IllegalStateException("Config already initialized");
        }

        Path resolvedPath = resolveAndCheck(path);
        userConfigPath = resolvedPath;
    }

    /**
     * 获取UserConfig单例实例
     *
     * @return UserConfig实例
     */
    public static UserConfig getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new UserConfig(userConfigPath);
                }
            }
        }
        return instance;
    }

    /**
     * 检查是否处于敏感模式
     *
     * @return 如果处于敏感模式返回true
     */
    public static boolean isSensitive() {
        String envValue = System.getenv("IS_SENSITIVE");
        if (envValue != null && envValue.equalsIgnoreCase("false")) {
            return false;
        }

        return getInstance().isSensitive;
    }

    /**
     * 获取敏感路径列表
     *
     * @return 敏感路径列表
     */
    public static List<String> getSensitivePaths() {
        return getInstance().getSensitivePathsList();
    }

    /**
     * 设置敏感模式标志
     *
     * @param sensitive 是否处于敏感模式
     */
    public static void setIsSensitive(boolean sensitive) {
        synchronized (LOCK) {
            UserConfig config = getInstance();
            config.isSensitive = sensitive;
            config.properties.setProperty("settings.is_sensitive", String.valueOf(sensitive));
        }
    }

    /**
     * 解析并检查配置文件路径
     *
     * @param path 配置文件路径
     * @return 解析后的路径
     * @throws IllegalArgumentException 如果配置文件不在项目根目录下
     */
    private static Path resolveAndCheck(Path path) {
        Path expandedPath = path.normalize();

        Path root = Paths.get(".").toAbsolutePath().normalize();
        try {
            Path relativePath = root.relativize(expandedPath);
            // 如果能获取相对路径，说明在项目根目录下
            return expandedPath;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Config file must be inside project root directory",
                e
            );
        }
    }

    /**
     * 获取敏感路径列表
     *
     * @return 敏感路径列表的副本
     */
    public List<String> getSensitivePathsList() {
        if (sensitivePaths == null) {
            synchronized (this) {
                if (sensitivePaths == null) {
                    String sensitivePathsStr = properties.getProperty(
                        "settings.sensitive_paths", ""
                    );

                    if (sensitivePathsStr != null && !sensitivePathsStr.isEmpty()) {
                        List<String> paths = new ArrayList<>();
                        String[] parts = sensitivePathsStr.split(",");
                        for (String part : parts) {
                            String trimmed = part.trim();
                            if (!trimmed.isEmpty()) {
                                paths.add(trimmed);
                            }
                        }
                        sensitivePaths = paths;
                    } else {
                        sensitivePaths = new ArrayList<>(DEFAULT_SENSITIVE_PATHS);
                    }
                }
            }
        }
        return new ArrayList<>(sensitivePaths);
    }

    /**
     * 获取配置属性
     *
     * @param key 属性键
     * @return 属性值，如果不存在返回null
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * 设置配置属性
     *
     * @param key 属性键
     * @param value 属性值
     */
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    /**
     * 保存配置到文件
     *
     * @param path 保存路径
     * @throws IOException 如果保存失败
     */
    public void save(Path path) throws IOException {
        synchronized (this) {
            try (Writer writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "OpenJiuwen User Configuration");
            }
        }
    }

    /**
     * 重置为默认配置
     */
    public static void reset() {
        synchronized (LOCK) {
            instance = null;
            userConfigPath = null;
        }
    }
}