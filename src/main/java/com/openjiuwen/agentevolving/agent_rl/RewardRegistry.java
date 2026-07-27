/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple in-memory registry for reward functions.
 */
public class RewardRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(RewardRegistry.class);

    private static final RewardRegistry DEFAULT_REGISTRY = new RewardRegistry();

    private final Map<String, RewardCallable> registry = new LinkedHashMap<>();

    /**
     * Functional interface for Python-style reward callables.
     */
    @FunctionalInterface
    public interface RewardCallable {
        Object apply(Object... args);
    }

    /**
     * Return the module-level default reward registry.
     *
     * @return module-level default reward registry instance
     */
    public static RewardRegistry rewardRegistry() {
        return DEFAULT_REGISTRY;
    }

    /**
     * Register a reward function into the module-level default registry.
     *
     * @param name reward function name
     * @param fn reward function
     * @return registered reward callable
     */
    public static RewardCallable registerReward(String name, RewardCallable fn) {
        rewardRegistry().register(name, fn);
        return fn;
    }

    /**
     * Register a reward function by name.
     *
     * @param name reward function name
     * @param fn reward function
     */
    public synchronized void register(String name, RewardCallable fn) {
        if (name == null || name.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_RL_REWARD_NAME_INVALID,
                    "error_msg", "reward name must be non-empty"
            );
        }
        LOG.info("register reward function: {}", name);
        registry.put(name, fn);
    }

    /**
     * Look up a reward function by name.
     *
     * @param name reward function name
     * @return reward callable registered by name
     */
    public synchronized RewardCallable get(String name) {
        RewardCallable rewardCallable = registry.get(name);
        if (rewardCallable == null) {
            throw ErrorHelper.buildError(StatusCode.AGENT_RL_REWARD_NOT_FOUND, "name", String.valueOf(name));
        }
        return rewardCallable;
    }

    /**
     * Return the list of all registered reward names.
     *
     * @return ordered reward names snapshot
     */
    public synchronized List<String> list() {
        return new ArrayList<>(registry.keySet());
    }
}
