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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for RewardRegistry.
 * <p>
 * Mirrors Python's {@code test_registry.py} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/reward/} and
 * {@code test_registry_e2e.py} in
 * {@code tests/system_tests/agent_evolving/agent_rl/reward/}.
 */
@DisplayName("Registry Tests")
class TestRegistry {

    @Test
    @DisplayName("register and get returns same callable")
    void testRegisterAndGetReturnsSameCallable() {
        RewardRegistry registry = new RewardRegistry();
        Function<Object, Double> reward = value -> ((Number) value).doubleValue() + 1.0;

        registry.register("r1", reward);

        assertThat(registry.get("r1")).isSameAs(reward);
        assertThat(registry.get("r1").apply(10)).isEqualTo(11.0);
    }

    @Test
    @DisplayName("list returns all registered names")
    void testListReturnsAllRegisteredNames() {
        RewardRegistry registry = new RewardRegistry();

        registry.register("a", ignored -> 1.0);
        registry.register("b", ignored -> 2.0);

        assertThat(registry.list()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    @DisplayName("register empty name raises value error")
    void testRegisterEmptyNameRaisesValueError() {
        RewardRegistry registry = new RewardRegistry();

        assertThatThrownBy(() -> registry.register("", ignored -> 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty");
    }

    @Test
    @DisplayName("get nonexistent raises key error")
    void testGetNonexistentRaisesKeyError() {
        RewardRegistry registry = new RewardRegistry();

        assertThatThrownBy(() -> registry.get("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    @DisplayName("decorator register reward")
    void testDecoratorRegisterReward() {
        RewardRegistry.getInstance().clear();
        Function<Object, Double> decoratedReward = rollout -> 0.5;

        Reward.register("r2", decoratedReward);

        assertThat(RewardRegistry.getInstance().get("r2")).isSameAs(decoratedReward);
        assertThat(Reward.get("r2").apply(Map.of())).isEqualTo(0.5);
    }
}
