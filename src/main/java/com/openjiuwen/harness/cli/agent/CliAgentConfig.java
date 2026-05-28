/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.agent;

import java.util.*;

/**
 * CLI agent configuration loader.
 * <p>
 * Mirrors Python's {@code load_config} in
 * {@code openjiuwen.harness.cli.agent.config}.
 */
public final class CliAgentConfig {

    private CliAgentConfig() {
    }

    /** Load agent config from file or defaults. */
    public static Map<String, Object> loadConfig(String configPath) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("language", "cn");
        config.put("mode", "full");
        config.put("workspace", System.getProperty("user.dir"));
        if (configPath != null) {
            config.put("config_path", configPath);
        }
        return config;
    }
}
