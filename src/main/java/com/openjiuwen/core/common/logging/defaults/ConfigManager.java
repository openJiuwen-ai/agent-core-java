/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging.defaults;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.security.PathChecker;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置管理器
 * 
 * <p>负责加载和管理应用配置，支持从 YAML 文件加载配置。
 * 
 * <p>对应 Python: default/config_manager.py
 */
public class ConfigManager {
    
    private Map<String, Object> config;
    
    /**
     * 使用默认配置创建 ConfigManager
     */
    public ConfigManager() {
        this(null);
    }
    
    /**
     * 从配置文件创建 ConfigManager
     * 
     * @param configPath 配置文件路径，如果为 null 则使用默认配置
     */
    public ConfigManager(String configPath) {
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
     * 加载配置文件
     */
    @SuppressWarnings("unchecked")
    private void loadConfig(String configPath) {
        try {
            if (configPath == null) {
                this.config = new HashMap<>(LoggingConstants.DEFAULT_LOG_CONFIG);
                processLoggingLevel();
                return;
            }
            
            // 解析真实路径
            Path realPath;
            try {
                realPath = Paths.get(configPath).toRealPath();
            } catch (IOException e) {
                realPath = Paths.get(configPath).toAbsolutePath().normalize();
            }
            
            // 检查敏感路径
            if (PathChecker.checkSensitivePath(realPath.toString())) {
                throw new JiuWenBaseException(
                    StatusCode.COMMON_LOG_PATH_INVALID.getCode(),
                    "the path is " + realPath
                );
            }
            
            try (InputStream inputStream = new FileInputStream(realPath.toFile())) {
                Yaml yaml = new Yaml();
                this.config = yaml.load(inputStream);
                
                if (this.config == null) {
                    this.config = new HashMap<>();
                }
                
                processLoggingLevel();
            }
            
        } catch (IOException e) {
            // 文件未找到时使用默认配置
            this.config = new HashMap<>();
            this.config.put("logging", Map.of("level", LoggingConstants.WARNING));
        } catch (JiuWenBaseException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof org.yaml.snakeyaml.error.YAMLException) {
                throw new JiuWenBaseException(
                    StatusCode.COMMON_LOG_CONFIG_PROCESS_ERROR.getCode(),
                    StatusCode.COMMON_LOG_CONFIG_PROCESS_ERROR.getMessage()
                        .replace("{error_msg}", "YAML configuration file format is incorrect: " + e.getMessage())
                );
            }
            throw new JiuWenBaseException(
                StatusCode.COMMON_LOG_CONFIG_PROCESS_ERROR.getCode(),
                StatusCode.COMMON_LOG_CONFIG_PROCESS_ERROR.getMessage()
                    .replace("{error_msg}", "unexpected error while loading configuration file: " + e.getMessage())
            );
        }
    }
    
    /**
     * 处理日志级别配置，将字符串转换为数值
     */
    @SuppressWarnings("unchecked")
    private void processLoggingLevel() {
        if (config.containsKey("logging")) {
            Object loggingObj = config.get("logging");
            if (loggingObj instanceof Map) {
                Map<String, Object> logging = (Map<String, Object>) loggingObj;
                Object levelObj = logging.get("level");
                if (levelObj instanceof String) {
                    String levelStr = ((String) levelObj).toUpperCase();
                    int levelValue = LoggingConstants.getLevelByName(levelStr, LoggingConstants.WARNING);
                    logging.put("level", levelValue);
                }
            }
        }
    }
    
    /**
     * 获取配置值
     * 
     * @param key 配置键，支持点分隔的嵌套路径（如 "logging.level"）
     * @param defaultValue 默认值
     * @return 配置值
     */
    @SuppressWarnings("unchecked")
    public Object get(String key, Object defaultValue) {
        String[] keys = key.split("\\.");
        Object value = config;
        
        for (String k : keys) {
            if (value instanceof Map) {
                value = ((Map<String, Object>) value).get(k);
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
     * 获取配置值（无默认值）
     */
    public Object get(String key) {
        return get(key, null);
    }
    
    /**
     * 获取完整配置
     */
    public Map<String, Object> getConfig() {
        return config;
    }
    
    /**
     * 检查配置是否包含指定键
     */
    public boolean contains(String key) {
        return get(key) != null;
    }
    
    // ==================== 静态实例和配置方法 ====================
    
    private static ConfigManager instance = new ConfigManager();
    private static ConfigDict configDict = new ConfigDict(instance);
    
    /**
     * 获取 ConfigManager 实例
     */
    public static ConfigManager getInstance() {
        return instance;
    }
    
    /**
     * 获取 ConfigDict 实例
     */
    public static ConfigDict getConfigDict() {
        return configDict;
    }
    
    /**
     * 配置应用
     * 
     * <p>用于外部项目指定自定义 YAML 配置路径。
     * 
     * @param configPath 配置文件路径
     */
    public static void configure(String configPath) {
        instance.reload(configPath);
        configDict.refresh();
    }
    
    /**
     * 配置字典类
     * 
     * <p>提供字典风格的配置访问接口。
     */
    public static class ConfigDict extends HashMap<String, Object> {
        
        private final ConfigManager configManager;
        
        public ConfigDict(ConfigManager configManager) {
            super(configManager.getConfig());
            this.configManager = configManager;
        }
        
        /**
         * 获取配置值
         */
        public Object get(String key, Object defaultValue) {
            return configManager.get(key, defaultValue);
        }
        
        /**
         * 刷新配置
         */
        public void refresh() {
            this.clear();
            this.putAll(configManager.getConfig());
        }
    }
}

