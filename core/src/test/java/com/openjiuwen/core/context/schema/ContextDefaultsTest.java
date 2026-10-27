/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the default context upper bounds of {@link ContextEngineConfig}.
 */
class ContextDefaultsTest {

    @Test
    void unconfiguredBoundsFallBackToSafeDefaults() {
        ContextEngineConfig noArg = new ContextEngineConfig();
        assertThat(noArg.getMaxContextMessageNum()).isEqualTo(200);
        assertThat(noArg.getDefaultWindowMessageNum()).isEqualTo(200);
        assertThat(noArg.getDefaultWindowRoundNum()).isEqualTo(10);

        ContextEngineConfig built = ContextEngineConfig.builder().build();
        assertThat(built.getMaxContextMessageNum()).isEqualTo(200);
        assertThat(built.getDefaultWindowMessageNum()).isEqualTo(200);
        assertThat(built.getDefaultWindowRoundNum()).isEqualTo(10);
    }

    @Test
    void partialConfigStillGetsDefaultsForMissingBounds() {
        ContextEngineConfig config = ContextEngineConfig.builder()
                .enableTiktokenCounter(true)
                .contextWindowTokens(128000)
                .maxContextMessageNum(64)
                .build();

        assertThat(config.getMaxContextMessageNum()).isEqualTo(64);
        assertThat(config.getDefaultWindowMessageNum()).isEqualTo(200);
        assertThat(config.getDefaultWindowRoundNum()).isEqualTo(10);
    }

    @Test
    void defaultBoundsFlowIntoRuntimeAgentConfig() {
        com.openjiuwen.core.singleagent.agents.ReActAgentConfig reactConfig =
                new com.openjiuwen.core.singleagent.agents.ReActAgentConfig();
        ContextEngineConfig config = ContextEngineConfig.builder().build();

        reactConfig.setContextEngineConfig(config);

        assertThat(reactConfig.getContextEngineConfig().getMaxContextMessageNum())
                .isEqualTo(200);
    }
}
