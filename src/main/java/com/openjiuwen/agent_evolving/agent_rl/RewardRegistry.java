/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

/**
 * Simple in-memory registry for reward functions.
 *
 * <p>Mirrors Python's {@code RewardRegistry} and module-level helpers in
 * {@code openjiuwen/agent_evolving/agent_rl/reward.py}.</p>
 */
public class RewardRegistry {

    private static final Logger LOGGER = Logger.getLogger(RewardRegistry.class.getName());
    private static final RewardRegistry DEFAULT_REGISTRY = new RewardRegistry();

    private final Map<String, RewardCallable> registry = new LinkedHashMap<>();

    /**
     * Mirrors Python's {@code RewardCallable = Callable[..., Any]}.
     */
    @FunctionalInterface
    public interface RewardCallable {
        Object apply(Object value);
    }

    public static RewardRegistry rewardRegistry() {
        return DEFAULT_REGISTRY;
    }

    public static UnaryOperator<RewardCallable> registerReward(String name) {
        return callable -> {
            rewardRegistry().register(name, callable);
            return callable;
        };
    }

    public synchronized void register(String name, RewardCallable fn) {
        if (name == null || name.isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_RL_REWARD_NAME_INVALID,
                    "error_msg", "reward name must be non-empty"
            );
        }
        LOGGER.info(() -> "register reward function: " + name);
        registry.put(name, fn);
    }

    public synchronized RewardCallable get(String name) {
        RewardCallable callable = registry.get(name);
        if (callable == null) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_RL_REWARD_NOT_FOUND,
                    "name", String.valueOf(name)
            );
        }
        return callable;
    }

    public synchronized List<String> list() {
        return new ArrayList<>(registry.keySet());
    }
}
