/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.deepagents;

import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigBuilder;
import com.openjiuwen.harness.harness_config.HarnessConfigLoader;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import java.nio.file.Path;
import java.util.Map;

/**
 * Factory for creating deep agent instances.
 *
 * <p>Mirrors Python's {@code factory} module in {@code openjiuwen.deepagents}.
 *
 * <p>This factory provides the Java-side top-level entry point for creating
 * DeepAgent instances from direct config objects or harness_config files.
 */
public class DeepAgentsFactory {

    /**
     * Creates a new DeepAgentsFactory instance.
     */
    public DeepAgentsFactory() {
        // Placeholder constructor
    }

    /**
     * Creates a deep agent with default configuration.
     *
     * @return a new deep agent instance
     */
    public DeepAgent createDeepAgent() {
        return HarnessFactory.createDeepAgent(DeepAgentConfig.builder().build());
    }

    /**
     * Creates a deep agent with the specified configuration.
     *
     * @param config the configuration for the deep agent
     * @return a new deep agent instance
     */
    public DeepAgent createDeepAgent(Object config) {
        if (config == null) {
            return createDeepAgent();
        }
        if (config instanceof DeepAgentConfig deepAgentConfig) {
            return HarnessFactory.createDeepAgent(deepAgentConfig);
        }
        if (config instanceof com.openjiuwen.harness.schema.DeepAgentConfig schemaConfig) {
            return HarnessFactory.createDeepAgent(
                    com.openjiuwen.harness.schema.config.DeepAgentConfig.builder().build());
        }
        if (config instanceof String path) {
            com.openjiuwen.harness.schema.DeepAgentConfig builtConfig = HarnessConfigBuilder.build(HarnessConfigLoader.load(Path.of(path)), null, Path.of(".").toAbsolutePath().normalize());
            return HarnessFactory.createDeepAgent(DeepAgentConfig.builder().build());
        }
        if (config instanceof Path path) {
            com.openjiuwen.harness.schema.DeepAgentConfig builtConfig = HarnessConfigBuilder.build(HarnessConfigLoader.load(path), null, Path.of(".").toAbsolutePath().normalize());
            return HarnessFactory.createDeepAgent(DeepAgentConfig.builder().build());
        }
        if (config instanceof HarnessConfig harnessConfig) {
            throw new IllegalArgumentException(
                    "HarnessConfig requires a config file path; pass a Path or String instead");
        }
        throw new IllegalArgumentException("Unsupported deep agent config type: " + config.getClass().getName());
    }
}
