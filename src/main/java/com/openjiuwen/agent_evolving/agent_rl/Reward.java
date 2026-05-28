// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl;

import java.util.function.Function;

/**
 * Reward calculation utilities for RL training.
 * <p>
 * Provides convenient static methods for working with the {@link RewardRegistry}.
 * Mirrors Python's module-level convenience functions from
 * {@code openjiuwen.agent_evolving.agent_rl.reward}.
 */
public final class Reward {
    
    private Reward() {
        // Utility class
    }
    
    /**
     * Register a reward function by name.
     * <p>
     * Convenience method for {@code RewardRegistry.getInstance().register(name, fn)}.
     *
     * @param name Reward function name
     * @param fn Reward function
     */
    public static void register(String name, Function<Object, Double> fn) {
        RewardRegistry.getInstance().register(name, fn);
    }
    
    /**
     * Get a reward function by name.
     * <p>
     * Convenience method for {@code RewardRegistry.getInstance().get(name)}.
     *
     * @param name Reward function name
     * @return Reward function
     */
    public static Function<Object, Double> get(String name) {
        return RewardRegistry.getInstance().get(name);
    }
    
    /**
     * Calculate reward from trajectory using a registered reward function.
     *
     * @param rewardName Name of the registered reward function
     * @param trajectory Trajectory object
     * @return Calculated reward value
     */
    public static double calculateReward(String rewardName, Object trajectory) {
        Function<Object, Double> fn = get(rewardName);
        Double result = fn.apply(trajectory);
        return result != null ? result : 0.0;
    }
    
    /**
     * Get reward from feedback.
     * <p>
     * Simple extraction of reward value from feedback object.
     *
     * @param feedback Feedback object (can be a Map with "reward" key, or a Number)
     * @return Reward value
     */
    @SuppressWarnings("unchecked")
    public static double getRewardFromFeedback(Object feedback) {
        if (feedback == null) {
            return 0.0;
        }
        
        // If feedback is a Map, try to extract "reward" key
        if (feedback instanceof java.util.Map) {
            Object rewardValue = ((java.util.Map<String, Object>) feedback).get("reward");
            if (rewardValue instanceof Number) {
                return ((Number) rewardValue).doubleValue();
            }
        }
        
        // If feedback is directly a Number
        if (feedback instanceof Number) {
            return ((Number) feedback).doubleValue();
        }
        
        return 0.0;
    }
}