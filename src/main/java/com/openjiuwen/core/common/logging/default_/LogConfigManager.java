// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.logging.default_;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志配置管理器
 *
 * <p>对应Python版本: openjiuwen/core/common/logging/default/config_manager.py</p>
 *
 * <p>管理日志配置，支持从YAML文件加载配置。</p>
 */
public final class LogConfigManager {

    private volatile Map<String, Object> config;
    private final Object lock = new Object();

    /**
     * 私有构造函数
     *
     * @param configPath 配置文件路径（可选）
     */
    public LogConfigManager(String configPath) {
        loadConfig(configPath);
    }

    /**
     * 重新加载配置
     *
     * @param configPath 配置文件路径
     */
    public void reload(String configPath) {
        loadConfig(configPath);
    }

    /**
     * 加载配置
     *
     * @param configPath 配置文件路径
     */
    @SuppressWarnings("unchecked")
    private void loadConfig(String configPath) {
        if (configPath == null) {
            // 使用默认配置
            this.config = new HashMap<>(LogConstants.DEFAULT_INNER_LOG_CONFIG);
            return;
        }

        try {
            Path path = Paths.get(configPath).toAbsolutePath();
            String content = Files.readString(path);

            // 简化实现：解析YAML（实际应使用SnakeYAML）
            Map<String, Object> yamlConfig = parseYaml(content);

            if (yamlConfig.containsKey("logging")) {
                Map<String, Object> loggingConfig = (Map<String, Object>) yamlConfig.get("logging");
                String levelStr = ((String) loggingConfig.getOrDefault("level", "WARNING")).toUpperCase();
                int level = LogConstants.parseLevel(levelStr);
                loggingConfig.put("level", level);
                this.config = loggingConfig;
            } else {
                this.config = new HashMap<>(LogConstants.DEFAULT_INNER_LOG_CONFIG);
            }
        } catch (Exception e) {
            // 加载失败时使用默认配置
            this.config = new HashMap<>(LogConstants.DEFAULT_INNER_LOG_CONFIG);
        }
    }

    /**
     * 获取配置值
     *
     * @param key 配置键（支持点分隔的嵌套键）
     * @param defaultValue 默认值
     * @return 配置值
     */
    @SuppressWarnings("unchecked")
    public Object get(String key, Object defaultValue) {
        String[] parts = key.split("\\.");
        Object value = config;

        for (String part : parts) {
            if (value instanceof Map) {
                value = ((Map<String, Object>) value).get(part);
                if (value == null) {
                    return defaultValue;
                }
            } else {
                return defaultValue;
            }
        }

        return value;
    }

    /**
     * 获取整个配置映射
     *
     * @return 配置映射
     */
    public Map<String, Object> getConfig() {
        return new HashMap<>(config);
    }

    /**
     * 简化的YAML解析（临时实现）
     *
     * @param content YAML内容
     * @return 解析后的映射
     */
    private Map<String, Object> parseYaml(String content) {
        // 简化实现：实际应使用SnakeYAML
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> logging = new HashMap<>();
        result.put("logging", logging);

        // 简单解析：按行解析
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            // 简化处理：仅解析 key: value 格式
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    // 移除引号
                    value = value.replace("\"", "").replace("'", "");

                    if (key.equals("level") || key.equals("log_path") ||
                        key.equals("log_file") || key.equals("format")) {
                        logging.put(key, value);
                    } else if (key.equals("backup_count")) {
                        logging.put(key, Integer.parseInt(value));
                    } else if (key.equals("max_bytes")) {
                        logging.put(key, Long.parseLong(value));
                    }
                }
            }
        }

        return result;
    }
}