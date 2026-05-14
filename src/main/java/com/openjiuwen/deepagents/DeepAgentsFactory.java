/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.deepagents;

import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;

/**
 * Factory for creating deep agent instances.
 *
 * <p>Mirrors Python's {@code factory} module in {@code openjiuwen.deepagents}.
 *
 * <p>This package is retained only as a compatibility bridge. Python `0.1.12`
 * moved active implementation ownership to {@code openjiuwen.harness}.
 * New code should depend on {@link HarnessFactory} instead.
 */
@Deprecated(forRemoval = false)
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
     * @return a new deep agent instance (placeholder)
     */
    public DeepAgent createDeepAgent() {
        return HarnessFactory.createDeepAgent();
    }

    /**
     * Creates a deep agent with the specified configuration.
     *
     * @param config the configuration for the deep agent
     * @return a new deep agent instance (placeholder)
     */
    public DeepAgent createDeepAgent(Object config) {
        if (config == null) {
            return HarnessFactory.createDeepAgent();
        }
        if (config instanceof DeepAgentConfig deepAgentConfig) {
            return HarnessFactory.createDeepAgent(deepAgentConfig);
        }
        throw new IllegalArgumentException(
                "DeepAgentsFactory compatibility bridge only accepts DeepAgentConfig; got "
                        + config.getClass().getName());
    }
}
