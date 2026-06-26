/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's reward registry tests in
 * {@code tests/unit_tests/agent_evolving/agent_rl/reward/test_registry.py}
 * and
 * {@code tests/system_tests/agent_evolving/agent_rl/reward/test_registry_e2e.py}.
 */
class RewardRegistryTest {

    @Test
    void registerAndGetReturnsSameCallable() {
        RewardRegistry registry = new RewardRegistry();
        RewardRegistry.RewardCallable callable = value -> (Integer) value + 1;

        registry.register("r1", callable);

        assertSame(callable, registry.get("r1"));
        assertEquals(11, registry.get("r1").apply(10));
    }

    @Test
    void listReturnsAllRegisteredNames() {
        RewardRegistry registry = new RewardRegistry();
        registry.register("a", value -> 1);
        registry.register("b", value -> 2);

        List<String> names = registry.list();

        assertEquals(List.of("a", "b"), names);
    }

    @Test
    void registerEmptyNameRaisesBaseError() {
        RewardRegistry registry = new RewardRegistry();

        BaseError error = assertThrows(BaseError.class, () -> registry.register("", value -> 1));

        assertEquals(StatusCode.AGENT_RL_REWARD_NAME_INVALID, error.getStatus());
        assertTrue(error.getMessage().toLowerCase().contains("non-empty")
                || error.getMessage().toLowerCase().contains("empty"));
    }

    @Test
    void getNonexistentRaisesBaseError() {
        RewardRegistry registry = new RewardRegistry();

        BaseError error = assertThrows(BaseError.class, () -> registry.get("nonexistent"));

        assertEquals(StatusCode.AGENT_RL_REWARD_NOT_FOUND, error.getStatus());
        assertTrue(error.getMessage().toLowerCase().contains("nonexistent")
                || error.getMessage().toLowerCase().contains("not found"));
    }

    @Test
    void decoratorRegisterRewardRegistersInDefaultRegistry() {
        String name = "decorated-" + UUID.randomUUID();
        RewardRegistry.RewardCallable callable = rollout -> 0.5d;

        RewardRegistry.RewardCallable decorated = RewardRegistry.registerReward(name).apply(callable);

        assertSame(callable, decorated);
        assertSame(callable, RewardRegistry.rewardRegistry().get(name));
    }

    @Test
    @SuppressWarnings("unchecked")
    void registryE2eRegisterGetList() {
        RewardRegistry registry = new RewardRegistry();
        RewardRegistry.RewardCallable myReward = rollout -> {
            Object score = ((Map<String, Object>) rollout).get("score");
            return score == null ? 0.5d : score;
        };

        registry.register("e2e_reward", myReward);
        registry.register("e2e_reward2", rollout -> 1.0d);

        assertSame(myReward, registry.get("e2e_reward"));
        assertEquals(0.9d, registry.get("e2e_reward").apply(Map.of("score", 0.9d)));
        assertTrue(registry.list().contains("e2e_reward"));
        assertTrue(registry.list().contains("e2e_reward2"));
    }

    @Test
    void registryE2eDecorator() {
        String name = "e2e-decorated-" + UUID.randomUUID();
        RewardRegistry.RewardCallable callable = rollout -> 0.42d;

        RewardRegistry.registerReward(name).apply(callable);

        assertSame(callable, RewardRegistry.rewardRegistry().get(name));
        assertEquals(0.42d, RewardRegistry.rewardRegistry().get(name).apply(null));
    }
}
