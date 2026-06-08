/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default tool-call optimizer configurations.
 *
 * <p>Mirrors Python's {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/default_configs.py}.</p>
 */
public final class DefaultConfigs {

    private DefaultConfigs() {
        // Utility class
    }

    /**
     * Default configuration for example generation.
     *
     * @return a mutable copy of the Python default configuration
     */
    public static Map<String, Object> defaultConfigEg() {
        Map<String, Object> config = new LinkedHashMap<>();
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
     * @return a mutable copy of the Python default configuration
     */
    public static Map<String, Object> defaultConfigDesc() {
        Map<String, Object> config = new LinkedHashMap<>();
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
}
