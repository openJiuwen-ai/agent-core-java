/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

import java.util.HashMap;
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
     * 
     * @param configFile YAML config file path (optional)
     * @param cliOverrides CLI argument overrides
     * @return Loaded configuration map
     */
    public static Map<String, Object> loadConfig(String configFile, Map<String, Object> cliOverrides) {
        // Start with default config
        Map<String, Object> config = getDefaultConfig();
        
        // TODO: Load YAML config file if provided
        
        // Apply CLI overrides
        if (cliOverrides != null) {
            deepMerge(config, cliOverrides);
        }
        
        return config;
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