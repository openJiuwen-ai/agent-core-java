/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Default configurations for tool optimizer.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.default_configs}.
 * 
 * @since 0.1.7
 */
public final class DefaultConfigs {
    /**
     * DefaultConfigs.
     * 
     * @since 0.1.7
     */
    private DefaultConfigs() {
        // Utility class
    }

    /**
     * Default configuration for example generation.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Object> defaultConfigEg() {
        Map<String, Object> config = new HashMap<>();
        config.put("gen_model_id", "gpt-5-mini");
        config.put("eval_model_id", "gpt-5-mini");
        config.put("verbose", 1);
        config.put("num_init_loop", 1);
        config.put("num_refine_steps", 1);
        config.put("num_feedback_steps", 2);
        config.put("score_eval_weight", 0.0);
        config.put("beam_width", 2);
        config.put("expand_num", 3);
        config.put("max_depth", 2);
        config.put("num_workers", 2);
        config.put("top_k", 5);
        return config;
    }

    /**
     * Default configuration for description optimization.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Object> defaultConfigDesc() {
        Map<String, Object> config = new HashMap<>();
        config.put("gen_model_id", "gpt-5-mini");
        config.put("eval_model_id", "gpt-5-mini");
        config.put("verbose", 1);
        config.put("num_init_loop", 1);
        config.put("num_feedback_steps", 2);
        config.put("score_eval_weight", 0.0);
        config.put("num_examples_for_desc", 4);
        config.put("beam_width", 2);
        config.put("expand_num", 2);
        config.put("max_depth", 2);
        config.put("num_workers", 2);
        config.put("top_k", 3);
        return config;
    }

    /**
     * Merge user config with defaults for example generation.
     * 
     * @param userConfig User-provided configuration
     * @return Merged configuration
     * @since 0.1.7
     */
    public static Map<String, Object> mergeWithDefaultEg(Map<String, Object> userConfig) {
        Map<String, Object> result = defaultConfigEg();
        if (userConfig != null) {
            result.putAll(userConfig);
        }
        return result;
    }

    /**
     * Merge user config with defaults for description optimization.
     * 
     * @param userConfig User-provided configuration
     * @return Merged configuration
     * @since 0.1.7
     */
    public static Map<String, Object> mergeWithDefaultDesc(Map<String, Object> userConfig) {
        Map<String, Object> result = defaultConfigDesc();
        if (userConfig != null) {
            result.putAll(userConfig);
        }
        return result;
    }
}
