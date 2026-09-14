/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Mirrors Python's config loader in
 * {@code openjiuwen/extensions/context_evolver/core/config.py}.
 */
public final class Config {

    private static final Map<String, Object> CONFIG = new LinkedHashMap<>();
    private static boolean configLoaded = false;

    private Config() {
    }

    private static Object convertValue(Object value) {
        if (!(value instanceof String stringValue)) {
            return value;
        }

        String lower = stringValue.toLowerCase();
        if ("true".equals(lower) || "yes".equals(lower) || "1".equals(lower)) {
            return true;
        }
        if ("false".equals(lower) || "no".equals(lower) || "0".equals(lower)) {
            return false;
        }

        try {
            if (stringValue.contains(".")) {
                return Double.parseDouble(stringValue);
            }
            return Integer.parseInt(stringValue);
        } catch (NumberFormatException ignored) {
            return stringValue;
        }
    }

    public static synchronized void load() {
        load(null, null);
    }

    @SuppressWarnings("unchecked")
    public static synchronized void load(String configPath, String envPath) {
        String rootDir = resolveDefaultRootDir();

        if (envPath == null) {
            envPath = Paths.get(rootDir, ".env").toString();
        }

        Path envFile = Paths.get(envPath);
        if (Files.exists(envFile)) {
            try (BufferedReader reader = Files.newBufferedReader(envFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                        int separator = trimmed.indexOf('=');
                        String key = trimmed.substring(0, separator).trim();
                        String value = trimmed.substring(separator + 1).trim();
                        CONFIG.put(key, convertValue(value));
                    }
                }
            } catch (IOException ignored) {
                // Mirrors Python's silent failure behavior for unreadable config inputs.
            }
        }

        if (configPath == null) {
            configPath = Paths.get(rootDir, "config.yaml").toString();
        }

        Path yamlFile = Paths.get(configPath);
        if (Files.exists(yamlFile)) {
            try (BufferedReader reader = Files.newBufferedReader(yamlFile, StandardCharsets.UTF_8)) {
                Object loaded = new Yaml().load(reader);
                if (loaded instanceof Map<?, ?> yamlConfig) {
                    for (Map.Entry<?, ?> entry : yamlConfig.entrySet()) {
                        String key = String.valueOf(entry.getKey());
                        if (!CONFIG.containsKey(key)) {
                            CONFIG.put(key, entry.getValue());
                        }
                    }
                }
            } catch (IOException ignored) {
                // Mirrors Python's silent failure behavior for unreadable config inputs.
            }
        }

        configLoaded = true;
    }

    public static synchronized Object get(String key) {
        return get(key, null);
    }

    public static synchronized Object get(String key, Object defaultValue) {
        if (!configLoaded) {
            load();
        }

        if (CONFIG.containsKey(key)) {
            return CONFIG.get(key);
        }

        String envValue = System.getenv(key);
        if (envValue != null) {
            return convertValue(envValue);
        }

        return defaultValue;
    }

    public static synchronized void setValue(String key, Object value) {
        if (!configLoaded) {
            load();
        }
        CONFIG.put(key, value);
    }

    public static synchronized void delete(String key) {
        if (!configLoaded) {
            load();
        }
        CONFIG.remove(key);
    }

    public static synchronized Map<String, Object> snapshot() {
        if (!configLoaded) {
            load();
        }
        return new LinkedHashMap<>(CONFIG);
    }

    public static synchronized void restore(Map<String, Object> snapshot) {
        CONFIG.clear();
        CONFIG.putAll(snapshot);
        configLoaded = true;
    }

    public static synchronized void reload() {
        CONFIG.clear();
        configLoaded = false;
        load();
    }

    private static String resolveDefaultRootDir() {
        String configured = System.getProperty("openjiuwen.context_evolver.root");
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).toAbsolutePath().normalize().toString();
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize().toString();
    }
}
