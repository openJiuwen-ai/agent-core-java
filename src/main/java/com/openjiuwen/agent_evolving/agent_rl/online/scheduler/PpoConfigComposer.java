// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.online.scheduler;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Build Hydra OmegaConf for online PPO training (built-in overlay or custom YAML).
 * <p>
 * Mirrors Python's {@code ppo_config.py} from
 * {@code openjiuwen.agent_evolving.agent_rl.online.scheduler.ppo_config}.
 */
public final class PpoConfigComposer {
    
    private static final Logger logger = Logger.getLogger(PpoConfigComposer.class.getName());
    
    private PpoConfigComposer() {
        // Utility class
    }
    
    /**
     * Compose online PPO config.
     * <p>
     * PLACEHOLDER: Java does not have Hydra/OmegaConf. This provides a basic Map-based config.
     * 
     * @param modelPath Model path
     * @param nGpusPerNode GPUs per node
     * @param configPath Custom config path (optional)
     * @return Configuration map
     */
    public static Map<String, Object> composeOnlinePpoConfig(
            String modelPath, int nGpusPerNode, String configPath) {
        
        Map<String, Object> cfg = new HashMap<>();
        
        // Default overlay values
        cfg.put("actor_rollout_ref", new HashMap<String, Object>());
        ((Map<String, Object>) cfg.get("actor_rollout_ref")).put("model", new HashMap<String, Object>());
        ((Map<String, Object>) ((Map<String, Object>) cfg.get("actor_rollout_ref")).get("model"))
            .put("path", modelPath);
        
        cfg.put("trainer", new HashMap<String, Object>());
        ((Map<String, Object>) cfg.get("trainer")).put("n_gpus_per_node", nGpusPerNode);
        ((Map<String, Object>) cfg.get("trainer")).put("default_local_dir", "/tmp/online_ppo_ckpt");
        
        if (configPath != null && !configPath.isEmpty()) {
            // PLACEHOLDER: Would load YAML config from path
            Path path = Paths.get(configPath);
            logger.info("Custom config path: " + path);
            // Would parse YAML and merge with defaults
        }
        
        logger.info("Composed PPO config for model: " + modelPath);
        return cfg;
    }
    
    /**
     * Get default PPO config.
     */
    public static Map<String, Object> getDefaultPpoConfig(String modelPath) {
        return composeOnlinePpoConfig(modelPath, 2, null);
    }
    
    /**
     * Online PPO VERL overlay defaults.
     */
    public static Map<String, Object> getOnlinePpoVerlOverlay() {
        Map<String, Object> overlay = new HashMap<>();
        
        // Default training parameters
        overlay.put("trainer", Map.of(
            "total_epochs", 1,
            "ppo_epochs", 1,
            "rollout_batch_size", 128,
            "rollout_batch_data_device", "cpu"
        ));
        
        // Default actor rollout parameters
        overlay.put("actor_rollout_ref", Map.of(
            "rollout", Map.of(
                "n", 1,
                "temperature", 1.0,
                "use_start", false
            )
        ));
        
        return overlay;
    }
}