/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.defaults;

import com.openjiuwen.core.common.security.PathChecker;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration manager — loads YAML config and provides dot-notation access.
 */
public class ConfigManager {

    private static final Map<String, Integer> NAME_TO_LEVEL = Map.of(
        "CRITICAL", 50, "FATAL", 50,
        "ERROR", 40,
        "WARNING", 30, "WARN", 30,
        "INFO", 20,
        "DEBUG", 10,
        "NOTSET", 0
    );

    private Map<String, Object> config;

    public ConfigManager() {
        this(null);
    }

    public ConfigManager(String configPath) {
        loadConfig(configPath);
    }

    public void reload(String configPath) {
        loadConfig(configPath);
    }

    @SuppressWarnings("unchecked")
    private void loadConfig(String configPath) {
        try {
            Map<String, Object> configDict;
            if (configPath == null) {
                configDict = new LinkedHashMap<>(DefaultLogConstants.defaultLogConfig());
            } else {
                Path realPath = Path.of(configPath).toRealPath();
                if (PathChecker.isSensitivePath(realPath.toString())) {
                    throw new IllegalArgumentException("Sensitive path: " + realPath);
                }
                try (InputStream is = Files.newInputStream(realPath)) {
                    Yaml yaml = new Yaml();
                    configDict = yaml.load(is);
                }
            }

            // Resolve logging level
            if (configDict.containsKey("logging") && configDict.get("logging") instanceof Map) {
                Map<String, Object> loggingSection = (Map<String, Object>) configDict.get("logging");
                String levelStr = String.valueOf(loggingSection.getOrDefault("level", "WARNING")).toUpperCase();
                loggingSection.put("level", NAME_TO_LEVEL.getOrDefault(levelStr, 30));
            }

            this.config = configDict;
        } catch (Exception e) {
            // Fallback config
            this.config = Map.of("logging", Map.of("level", 30));
        }
    }

    /**
     * Get a value by dot-separated key path.
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

    public Object get(String key) {
        return get(key, null);
    }

    public Map<String, Object> getConfig() {
        return config;
    }
}
