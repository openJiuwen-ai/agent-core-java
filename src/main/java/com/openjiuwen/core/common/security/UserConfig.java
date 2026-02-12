package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 用户配置管理
 * 
 * <p>使用单例模式管理用户配置，包括敏感路径配置等。
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public class UserConfig {

    private static final String DEFAULT_SENSITIVE_PATH_STR = 
        "/etc/passwd,/etc/shadow,/etc/hosts,/etc/hostname,/etc/ssh/," +
        "C:\\Windows\\System32\\,C:\\Windows\\SysWOW64\\,C:\\Windows\\System\\";

    private static final List<String> DEFAULT_SENSITIVE_PATHS = Arrays.asList(
        "/etc/passwd", "/etc/shadow", "/etc/hosts", "/etc/hostname", "/etc/ssh/",
        "C:\\Windows\\System32\\", "C:\\Windows\\SysWOW64\\", "C:\\Windows\\System\\"
    );

    private static Path userConfigPath = null;
    private final Properties config;
    private boolean isSensitive;
    private List<String> sensitivePaths;

    private UserConfig(Path configPath) {
        this.config = new Properties();
        this.isSensitive = true;
        this.sensitivePaths = null;

        if (configPath != null && Files.isRegularFile(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                config.load(in);
                this.isSensitive = Boolean.parseBoolean(
                    config.getProperty("settings.is_sensitive", "true")
                );
            } catch (IOException e) {
                // Use defaults
                loadDefaults();
            }
        } else {
            loadDefaults();
        }
    }

    /**
     * 加载默认配置
     */
    private void loadDefaults() {
        config.setProperty("settings.is_sensitive", "true");
        config.setProperty("settings.sensitive_paths", DEFAULT_SENSITIVE_PATH_STR);
    }

    /**
     * Bill Pugh单例模式
     */
    private static class Holder {
        private static final UserConfig INSTANCE = new UserConfig(userConfigPath);
    }

    /**
     * 获取UserConfig单例实例
     * 
     * @return UserConfig实例
     */
    public static UserConfig getConfig() {
        return Holder.INSTANCE;
    }

    /**
     * 设置配置文件路径
     * 
     * <p>必须在首次调用{@link #getConfig()}之前调用。
     * 
     * @param path 配置文件路径
     * @throws JiuWenBaseException 如果配置已初始化或路径不在根目录内
     */
    public static void setConfigPath(Path path) {
        // Check if already initialized (simplified check)
        if (userConfigPath != null) {
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            params.put("error_msg", "config already initialized");
            throw new JiuWenBaseException(
                StatusCode.COMMON_USER_CONFIG_PROCESS_ERROR.getCode(),
                StatusCode.COMMON_USER_CONFIG_PROCESS_ERROR.formatMessage(params)
            );
        }

        try {
            Path resolvedPath = path.toRealPath();
            Path root = Paths.get("").toAbsolutePath();

            if (!resolvedPath.startsWith(root)) {
                java.util.Map<String, Object> params2 = new java.util.HashMap<>();
                params2.put("error_msg", "config file must inside root");
                throw new JiuWenBaseException(
                    StatusCode.COMMON_USER_CONFIG_PROCESS_ERROR.getCode(),
                    StatusCode.COMMON_USER_CONFIG_PROCESS_ERROR.formatMessage(params2)
                );
            }

            userConfigPath = resolvedPath;
        } catch (IOException e) {
            java.util.Map<String, Object> params3 = new java.util.HashMap<>();
            params3.put("error_msg", "failed to resolve config path");
            JiuWenBaseException exception = new JiuWenBaseException(
                StatusCode.COMMON_USER_CONFIG_PROCESS_ERROR.getCode(),
                StatusCode.COMMON_USER_CONFIG_PROCESS_ERROR.formatMessage(params3)
            );
            exception.initCause(e);
            throw exception;
        }
    }

    /**
     * 检查是否启用敏感检测
     * 
     * @return 如果启用返回true，否则返回false
     */
    public static boolean isSensitive() {
        String envValue = System.getenv("IS_SENSITIVE");
        if ("false".equalsIgnoreCase(envValue)) {
            return false;
        }
        return getConfig().isSensitiveFlag();
    }

    /**
     * 获取敏感路径列表
     * 
     * @return 敏感路径列表
     */
    public static List<String> getSensitivePaths() {
        return getConfig().getSensitivePathsList();
    }

    /**
     * 设置敏感检测标志
     * 
     * @param isSensitive 是否启用敏感检测
     */
    public static void setIsSensitive(boolean isSensitive) {
        getConfig().isSensitive = isSensitive;
    }

    /**
     * 获取敏感检测标志（实例方法）
     * 
     * @return 如果启用返回true，否则返回false
     */
    public boolean isSensitiveFlag() {
        return this.isSensitive;
    }

    /**
     * 获取敏感路径列表（实例方法）
     * 
     * @return 敏感路径列表的副本
     */
    public List<String> getSensitivePathsList() {
        if (sensitivePaths == null) {
            String pathsStr = config.getProperty("settings.sensitive_paths", "");
            if (pathsStr.isEmpty()) {
                sensitivePaths = new ArrayList<>(DEFAULT_SENSITIVE_PATHS);
            } else {
                sensitivePaths = new ArrayList<>();
                for (String path : pathsStr.split(",")) {
                    String trimmed = path.trim();
                    if (!trimmed.isEmpty()) {
                        sensitivePaths.add(trimmed);
                    }
                }
            }
        }
        return new ArrayList<>(sensitivePaths);
    }
}

