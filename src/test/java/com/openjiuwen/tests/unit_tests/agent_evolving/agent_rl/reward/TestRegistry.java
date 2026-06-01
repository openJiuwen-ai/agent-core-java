/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.reward;

import com.openjiuwen.agent_evolving.agent_rl.Reward;
import com.openjiuwen.agent_evolving.agent_rl.RewardRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for RewardRegistry.
 * <p>
 * Mirrors Python's {@code test_registry_e2e.py} in
 * {@code tests/system_tests/agent_evolving/agent_rl/reward/}.
 */
@DisplayName("Registry Tests")
class TestRegistry {

    @Test
    @DisplayName("register, get and list")
    void testRegistryE2eRegisterGetList() {
        RewardRegistry registry = new RewardRegistry();
        Function<Object, Double> myReward = rollout -> {
            if (rollout instanceof Map<?, ?> map && map.get("score") instanceof Number score) {
                return score.doubleValue();
            }
            return 0.5;
        };

        registry.register("e2e_reward", myReward);
        assertThat(registry.get("e2e_reward")).isSameAs(myReward);
        assertThat(registry.get("e2e_reward").apply(Map.of("score", 0.9))).isEqualTo(0.9);

        registry.register("e2e_reward2", ignored -> 1.0);
        assertThat(registry.list()).contains("e2e_reward", "e2e_reward2");
    }

    @Test
    @DisplayName("module-level registration helper")
    void testRegistryE2eDecoratorEquivalent() {
        RewardRegistry.getInstance().clear();
        Function<Object, Double> decoratedReward = ignored -> 0.42;

        Reward.register("e2e_decorated", decoratedReward);

        assertThat(RewardRegistry.getInstance().get("e2e_decorated")).isSameAs(decoratedReward);
        assertThat(Reward.get("e2e_decorated").apply(null)).isEqualTo(0.42);
    }
}
