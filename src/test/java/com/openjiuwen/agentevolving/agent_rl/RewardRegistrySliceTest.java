package com.openjiuwen.agentevolving.agent_rl;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardRegistrySliceTest {

    @Test
    void registerAndGetReturnSameCallable() {
        RewardRegistry registry = new RewardRegistry();
        RewardRegistry.RewardCallable fn = args -> ((int) args[0]) + 1;

        registry.register("r1", fn);

        assertSame(fn, registry.get("r1"));
        assertEquals(11, registry.get("r1").apply(10));
    }

    @Test
    void listReturnsAllRegisteredNames() {
        RewardRegistry registry = new RewardRegistry();
        registry.register("a", args -> 1);
        registry.register("b", args -> 2);

        List<String> names = registry.list();

        assertEquals(Set.of("a", "b"), Set.copyOf(names));
    }

    @Test
    void registerEmptyNameRaisesValidationError() {
        RewardRegistry registry = new RewardRegistry();

        BaseError error = assertThrows(BaseError.class, () -> registry.register("", args -> 1));

        assertEquals(StatusCode.AGENT_RL_REWARD_NAME_INVALID, error.getStatus());
        assertTrue(error.getMessage().toLowerCase().contains("non-empty"));
    }

    @Test
    void getMissingRewardRaisesValidationError() {
        RewardRegistry registry = new RewardRegistry();

        BaseError error = assertThrows(BaseError.class, () -> registry.get("nonexistent"));

        assertEquals(StatusCode.AGENT_RL_REWARD_NOT_FOUND, error.getStatus());
        assertTrue(error.getMessage().toLowerCase().contains("nonexistent"));
    }

    @Test
    void registerRewardAddsCallableToDefaultRegistry() {
        String name = "unit_test_reward_registry_decorated";
        RewardRegistry.RewardCallable fn = args -> 0.5;

        RewardRegistry.RewardCallable returned = RewardRegistry.registerReward(name, fn);

        assertSame(fn, returned);
        assertSame(fn, RewardRegistry.rewardRegistry().get(name));
        assertEquals(0.5, RewardRegistry.rewardRegistry().get(name).apply(new Object[0]));
    }
}
