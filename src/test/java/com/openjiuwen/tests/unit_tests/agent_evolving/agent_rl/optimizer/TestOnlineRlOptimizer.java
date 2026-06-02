/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.optimizer;

import com.openjiuwen.agent_evolving.agent_rl.config.RLConfig;
import com.openjiuwen.agent_evolving.agent_rl.config.TrainingConfig;
import com.openjiuwen.agent_evolving.agent_rl.optimizer.OnlineRLOptimizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for OnlineRlOptimizer.
 * <p>
 * Mirrors Python's {@code test_online_rl_optimizer.py} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/optimizer/}.
 */
@DisplayName("OnlineRlOptimizer Tests")
class TestOnlineRlOptimizer {

    @Test
    @DisplayName("setup gateway rejects legacy HTTP gateway URL")
    void testSetupGatewayRejectsLegacyHttpGatewayUrl() {
        OnlineRLOptimizer optimizer = new OnlineRLOptimizer(minimalOnlineConfig());

        assertThatThrownBy(() -> optimizer.setupGateway("http://127.0.0.1:18080/v1", 10.0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("setup_gateway() no longer accepts HTTP Gateway URLs")
                .hasMessageContaining("setup_redis()");
    }

    @Test
    @DisplayName("setup gateway accepts redis URL for transition")
    void testSetupGatewayAcceptsRedisUrlForTransition() {
        OnlineRLOptimizer optimizer = new OnlineRLOptimizer(minimalOnlineConfig());

        OnlineRLOptimizer returned = optimizer.setupGateway("redis://127.0.0.1:6379/0", 15.0, 8);

        assertThat(returned).isSameAs(optimizer);
        assertThat(optimizer.getRedisUrl()).isEqualTo("redis://127.0.0.1:6379/0");
        assertThat(optimizer.getPollInterval()).isEqualTo(15.0);
        assertThat(optimizer.getMinSamples()).isEqualTo(8);
    }

    private static RLConfig minimalOnlineConfig() {
        TrainingConfig training = new TrainingConfig();
        training.setExperimentName("exp_test");
        training.setProjectName("proj");
        training.setModelPath("/tmp/model");
        return new RLConfig(training);
    }
}
