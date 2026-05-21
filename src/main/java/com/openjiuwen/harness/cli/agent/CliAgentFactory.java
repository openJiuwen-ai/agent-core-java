/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.agent;

/**
 * CLI agent factory — creates agents from CLI config.
 * <p>
 * Mirrors Python's {@code factory} in
 * {@code openjiuwen.harness.cli.agent.factory}.
 */
public final class CliAgentFactory {

    private CliAgentFactory() {
    }

    /** Create an agent from the given config. */
    public static Object createFromConfig(java.util.Map<String, Object> config) {
        // Delegate to DeepAgent factory
        return null; // placeholder — actual creation uses DeepAgentFactory
    }
}
