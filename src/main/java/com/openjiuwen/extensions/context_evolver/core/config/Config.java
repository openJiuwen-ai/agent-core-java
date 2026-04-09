/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.extensions.context_evolver.core.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.config}.
 * 
 * Configuration loader for context_evolver.
 * Loads configuration from .env file and config.yaml file.
 */
public class Config {
    
    private static final Logger log = LoggerFactory.getLogger(Config.class);
    
    private static final Map<String, Object> config = new ConcurrentHashMap<>();
    private static volatile boolean configLoaded = false;
    
    private Config() {
        // Utility class
    }
    
    /**
     * Convert string values to appropriate types.
     */
    private static Object convertValue(String value) {
        if (value == null) {
            return null;
        }
        
        String lower = value.toLowerCase();
        if ("true".equals(lower) || "yes".equals(lower) || "1".equals(lower)) {
            return true;
        }
        if ("false".equals(lower) || "no".equals(lower) || "0".equals(lower)) {
            return false;
        }
        
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }
    
    /**
     * Load configuration from .env and YAML files.
     */
    public static synchronized void load() {
        load(null, null);
    }
    
    /**
     * Load configuration from specified paths.
     *
     * @param configPath path to YAML config file
     * @param envPath    path to .env file
     */
    @SuppressWarnings("unchecked")
    public static synchronized void load(String configPath, String envPath) {
        if (configLoaded) {
            return;
        }
        
        String rootDir = resolveDefaultRootDir();
        
        // Load .env file
        if (envPath == null) {
            envPath = Paths.get(rootDir, ".env").toString();
        }
        
        Path envFilePath = Paths.get(envPath);
        if (Files.exists(envFilePath)) {
            try (BufferedReader reader = Files.newBufferedReader(envFilePath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                        int idx = line.indexOf('=');
                        String key = line.substring(0, idx).trim();
                        String value = line.substring(idx + 1).trim();
                        config.put(key, convertValue(value));
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to load .env file: {}", e.getMessage());
            }
        }
        
        // Load config.yaml
        if (configPath == null) {
            configPath = Paths.get(rootDir, "config.yaml").toString();
        }
        
        Path configFilePath = Paths.get(configPath);
        if (Files.exists(configFilePath)) {
            try {
                Yaml yaml = new Yaml();
                try (InputStream is = Files.newInputStream(configFilePath)) {
                    Map<String, Object> yamlConfig = yaml.load(is);
                    if (yamlConfig != null) {
                        for (Map.Entry<String, Object> entry : yamlConfig.entrySet()) {
                            config.putIfAbsent(entry.getKey(), entry.getValue());
                        }
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to load config.yaml: {}", e.getMessage());
            }
        }

        configLoaded = true;
    }
    
    /**
     * Get a configuration value.
     *
     * @param key configuration key
     * @return the value or null
     */
    public static Object get(String key) {
        return get(key, null);
    }
    
    /**
     * Get a configuration value with default.
     *
     * @param key          configuration key
     * @param defaultValue default value
     * @return the value or default
     */
    public static Object get(String key, Object defaultValue) {
        if (!configLoaded) {
            load();
        }

        if (config.containsKey(key)) {
            return config.get(key);
        }

        String envValue = System.getenv(key);
        if (envValue != null) {
            return convertValue(envValue);
        }

        return defaultValue;
    }
    
    /**
     * Get a string configuration value.
     */
    public static String getString(String key) {
        Object value = get(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * Get a string configuration value with default.
     */
    public static String getString(String key, String defaultValue) {
        Object value = get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * Get an integer configuration value.
     */
    public static int getInt(String key, int defaultValue) {
        Object value = get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * Get a boolean configuration value.
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        Object value = get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    /**
     * Set a configuration value.
     */
    public static void setValue(String key, Object value) {
        if (!configLoaded) {
            load();
        }
        config.put(key, value);
    }
    
    /**
     * Delete a configuration value.
     */
    public static void delete(String key) {
        if (!configLoaded) {
            load();
        }
        config.remove(key);
    }
    
    /**
     * Take a snapshot of current configuration.
     */
    public static Map<String, Object> snapshot() {
        if (!configLoaded) {
            load();
        }
        return new HashMap<>(config);
    }
    
    /**
     * Restore configuration from a snapshot.
     */
    public static void restore(Map<String, Object> snap) {
        config.clear();
        config.putAll(snap);
        configLoaded = true;
    }
    
    /**
     * Force reload configuration from files.
     */
    public static synchronized void reload() {
        reload(null, null);
    }

    /**
     * Force reload configuration from explicit files.
     */
    public static synchronized void reload(String configPath, String envPath) {
        config.clear();
        configLoaded = false;
        load(configPath, envPath);
    }

    private static String resolveDefaultRootDir() {
        String configuredRoot = System.getProperty("openjiuwen.context_evolver.root");
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            return Paths.get(configuredRoot).toAbsolutePath().normalize().toString();
        }

        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path current = cwd;
        while (current != null) {
            Path resourcesRoot = current.resolve(
                Paths.get("src", "main", "resources", "com", "openjiuwen", "extensions", "context_evolver")
            );
            if (Files.exists(resourcesRoot)) {
                return resourcesRoot.toString();
            }

            Path sourceRoot = current.resolve(
                Paths.get("src", "main", "java", "com", "openjiuwen", "extensions", "context_evolver")
            );
            if (Files.exists(sourceRoot)) {
                return sourceRoot.toString();
            }

            current = current.getParent();
        }

        return cwd.toString();
    }
}
