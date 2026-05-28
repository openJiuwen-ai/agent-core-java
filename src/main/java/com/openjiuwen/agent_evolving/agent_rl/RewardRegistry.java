// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Simple in-memory registry for reward functions.
 * <p>
 * Mirrors Python's {@code RewardRegistry} class from
 * {@code openjiuwen.agent_evolving.agent_rl.reward}.
 * 
 * <p>Usage:
 * <pre>
 * RewardRegistry registry = RewardRegistry.getInstance();
 * registry.register("my_reward", trajectory -> calculateScore(trajectory));
 * Function&lt;Object, Double&gt; fn = registry.get("my_reward");
 * Double reward = fn.apply(myTrajectory);
 * </pre>
 */
public class RewardRegistry {
    
    private static final Logger logger = Logger.getLogger(RewardRegistry.class.getName());
    
    /** Module-level default registry (singleton) */
    private static final RewardRegistry INSTANCE = new RewardRegistry();
    
    private final Map<String, Function<Object, Double>> registry = new HashMap<>();
    
    /**
     * Get the default singleton registry instance.
     * 
     * @return Default registry instance
     */
    public static RewardRegistry getInstance() {
        return INSTANCE;
    }
    
    /**
     * Register a reward function by name.
     * <p>
     * Mirrors Python: register(name, fn)
     *
     * @param name Reward function name (must be non-empty)
     * @param fn Reward function that takes a trajectory and returns a reward value
     * @throws IllegalArgumentException if name is null or empty
     */
    public void register(String name, Function<Object, Double> fn) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Reward name must be non-empty");
        }
        logger.info("Register reward function: " + name);
        registry.put(name, fn);
    }
    
    /**
     * Look up a reward function by name.
     * <p>
     * Mirrors Python: get(name)
     *
     * @param name Reward function name
     * @return Reward function
     * @throws IllegalArgumentException if name not found
     */
    public Function<Object, Double> get(String name) {
        if (!registry.containsKey(name)) {
            throw new IllegalArgumentException("Reward function not found: " + name);
        }
        return registry.get(name);
    }
    
    /**
     * Return the list of all registered reward names.
     * <p>
     * Mirrors Python: list()
     *
     * @return List of registered reward names
     */
    public List<String> list() {
        return new ArrayList<>(registry.keySet());
    }
    
    /**
     * Check if a reward function is registered.
     *
     * @param name Reward function name
     * @return true if registered
     */
    public boolean contains(String name) {
        return registry.containsKey(name);
    }
    
    /**
     * Unregister a reward function.
     *
     * @param name Reward function name
     */
    public void unregister(String name) {
        registry.remove(name);
    }
    
    /**
     * Clear all registered reward functions.
     */
    public void clear() {
        registry.clear();
    }
}
