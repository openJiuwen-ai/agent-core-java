/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Backward compatibility tests for legacy imports.
 *
 * <p>Mirrors Python's {@code test_backward_compatibility.py} in
 * {@code tests/unit_tests/agent/}.
 */
@DisplayName("Backward Compatibility")
class BackwardCompatibilityTest {

    @Test
    @DisplayName("AgentCard import works without issues")
    void testNewImportsNoWarning() {
        AgentCard card = AgentCard.builder()
                .name("test")
                .description("test")
                .build();
        assertThat(card).isNotNull();
    }

    @Test
    @DisplayName("legacy module imports work")
    void testLegacyModuleImportsWork() {
        assertThatNoException().isThrownBy(() -> {
            var config = new com.openjiuwen.core.singleagent.legacy.config.AgentConfig();
            assertThat(config).isNotNull();
        });

        assertThatNoException().isThrownBy(() -> {
            var config = new com.openjiuwen.core.singleagent.legacy.config.LLMCallConfig();
            assertThat(config).isNotNull();
        });

        assertThatNoException().isThrownBy(() -> {
            var config = new com.openjiuwen.core.singleagent.legacy.config.ConstrainConfig();
            assertThat(config).isNotNull();
        });
    }

    @Test
    @DisplayName("legacy ReActAgent can be instantiated")
    void testLegacyReActAgentCanBeInstantiated() {
        assertThatNoException().isThrownBy(() -> {
            var card = AgentCard.builder()
                    .name("legacy_test")
                    .description("Legacy agent test")
                    .build();
            var agent = new com.openjiuwen.core.singleagent.legacy.ReActAgent(card);
            assertThat(agent).isNotNull();
        });
    }

    @Test
    @DisplayName("legacy LegacyReActAgent can be instantiated")
    void testLegacyLegacyReActAgentCanBeInstantiated() {
        assertThatNoException().isThrownBy(() -> {
            var card = AgentCard.builder()
                    .name("legacy_compat_test")
                    .description("Legacy compat test")
                    .build();
            var agent = new com.openjiuwen.core.singleagent.legacy.LegacyReActAgent(card);
            assertThat(agent).isNotNull();
        });
    }
}
