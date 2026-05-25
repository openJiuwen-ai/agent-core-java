/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration loader for online RL runtime.
 * <p>
 * Mirrors Python's {@code loader} module in
 * {@code openjiuwen.agent_evolving.agent_rl.online.launcher.loader}.
 */
public class LauncherLoader {

    private static final String DEFAULT_CONFIG_FILENAME = "online_rl_config.yaml";

    /**
     * Load configuration from YAML file with CLI overrides.
     * <p>
     * Mirrors Python's load_runtime_config using OmegaConf.
     * 
     * @param configFile YAML config file path (optional)
     * @param cliOverrides CLI argument overrides
     * @return Loaded configuration map
     */
    public static Map<String, Object> loadConfig(String configFile, Map<String, Object> cliOverrides) {
        // Start with default config (mirrors Python: base_layer = OmegaConf.create(BUILTIN_ONLINE_RL_CONFIG))
        Map<String, Object> config = getDefaultConfig();
        
        // Load YAML config file if provided
        // Mirrors Python: layered_cfgs.append(OmegaConf.load(resolved_path))
        if (configFile != null && !configFile.isEmpty()) {
            try {
                java.nio.file.Path configPath = java.nio.file.Paths.get(configFile);
                if (java.nio.file.Files.exists(configPath)) {
                    Map<String, Object> yamlConfig = loadYamlConfig(configPath);
                    deepMerge(config, yamlConfig);
                }
            } catch (Exception e) {
                // Log warning but continue with defaults
                System.err.println("Warning: Failed to load config file: " + e.getMessage());
            }
        }
        
        // Apply CLI overrides (mirrors Python: layered_cfgs.append(OmegaConf.create(cli_overrides)))
        if (cliOverrides != null) {
            deepMerge(config, cliOverrides);
        }
        
        return config;
    }
    
    /**
     * Load YAML configuration file.
     * <p>
     * Simple YAML loader using basic parsing.
     * For production, consider using Jackson YAML or SnakeYAML.
     */
    private static Map<String, Object> loadYamlConfig(java.nio.file.Path configPath) throws IOException {
        // Simple YAML loading - in production, use Jackson or SnakeYAML
        // This is a placeholder that reads the file as properties for basic key-value pairs
        // Full YAML parsing would require a proper YAML library
        
        Map<String, Object> result = new HashMap<>();
        List<String> lines = java.nio.file.Files.readAllLines(configPath);
        
        for (String line : lines) {
            line = line.trim();
            // Skip comments and empty lines
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            // Parse simple key: value pairs
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                // Remove quotes if present
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                // Try to parse as number or boolean
                Object parsedValue = parseValue(value);
                result.put(key, parsedValue);
            }
        }
        
        return result;
    }
    
    /**
     * Parse a string value to appropriate type (Integer, Double, Boolean, or String).
     */
    private static Object parseValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        // Try boolean
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        // Try integer
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Not an integer
        }
        // Try double
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            // Not a number
        }
        // Return as string
        return value;
    }

    /**
     * Get default configuration.
     * 
     * @return Default config map
     */
    public static Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new HashMap<>();
        
        // Inference defaults
        Map<String, Object> inference = new HashMap<>();
        inference.put("model_path", "/path/to/your/model");
        inference.put("model_name", "Qwen3-4B-Thinking-2507");
        inference.put("host", "127.0.0.1");
        inference.put("gpu_ids", "0,1");
        inference.put("tp", 2);
        config.put("inference", inference);
        
        // Gateway defaults
        Map<String, Object> gateway = new HashMap<>();
        gateway.put("host", "127.0.0.1");
        gateway.put("record_dir", "records");
        gateway.put("log_level", "info");
        gateway.put("disable_trajectory_collection", true);
        config.put("gateway", gateway);
        
        // Training defaults
        Map<String, Object> training = new HashMap<>();
        training.put("gpu_ids", "4,5");
        training.put("threshold", 4);
        training.put("scan_interval", 30);
        config.put("training", training);
        
        // Trajectory defaults
        Map<String, Object> trajectory = new HashMap<>();
        trajectory.put("batch_size", 4);
        trajectory.put("mode", "feedback_level");
        config.put("trajectory", trajectory);
        
        // Jiuwen defaults
        Map<String, Object> jiuwen = new HashMap<>();
        jiuwen.put("enabled", true);
        jiuwen.put("app_host", "127.0.0.1");
        jiuwen.put("web_host", "127.0.0.1");
        config.put("jiwen", jiuwen);
        
        config.put("demo", false);
        
        return config;
    }

    /**
     * Deep merge two maps.
     * 
     * @param base Base map to merge into
     * @param overlay Overlay map
     */
    private static void deepMerge(Map<String, Object> base, Map<String, Object> overlay) {
        for (Map.Entry<String, Object> entry : overlay.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map && base.get(key) instanceof Map) {
                deepMerge((Map<String, Object>) base.get(key), (Map<String, Object>) value);
            } else {
                base.put(key, value);
            }
        }
    }

    public static String getDefaultConfigFilename() {
        return DEFAULT_CONFIG_FILENAME;
    }
}