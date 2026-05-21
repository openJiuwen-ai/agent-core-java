/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Registry for rollout processors that auto-discovers and manages them.
 * <p>
 * Mirrors Python's {@code ProcessorsRegistry} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.coordinator.processors}.
 */
public class ProcessorsRegistry {

    private final Map<String, Function<List<RolloutWithReward>, List<RolloutWithReward>[]>> classifiers = new HashMap<>();
    private final Map<String, BiFunction<List<RolloutWithReward>, List<RolloutWithReward>, Boolean>> validators = new HashMap<>();
    private final Map<String, TriFunction<List<RolloutWithReward>, List<RolloutWithReward>, Integer, List<RolloutWithReward>>> samplers = new HashMap<>();

    /**
     * Initialize registry with default processors.
     */
    public ProcessorsRegistry() {
        // Register default processors
        classifiers.put("default_classify_rollouts", RolloutProcessors::defaultClassifyRollouts);
        validators.put("default_validate_stop", RolloutProcessors::defaultValidateStop);
        validators.put("validate_stop_balanced", 
            (pos, neg) -> RolloutProcessors.validateStopBalanced(pos, neg, 8));
        samplers.put("default_sampling", 
            (pos, neg, max) -> RolloutProcessors.defaultSampling(pos, neg, max));
    }

    /**
     * Register a classifier function.
     * 
     * @param name Classifier name
     * @param classifier Classifier function
     */
    public void registerClassifier(String name, Function<List<RolloutWithReward>, List<RolloutWithReward>[]> classifier) {
        classifiers.put(name, classifier);
    }

    /**
     * Register a validator function.
     * 
     * @param name Validator name
     * @param validator Validator function
     */
    public void registerValidator(String name, BiFunction<List<RolloutWithReward>, List<RolloutWithReward>, Boolean> validator) {
        validators.put(name, validator);
    }

    /**
     * Register a sampler function.
     * 
     * @param name Sampler name
     * @param sampler Sampler function
     */
    public void registerSampler(String name, TriFunction<List<RolloutWithReward>, List<RolloutWithReward>, Integer, List<RolloutWithReward>> sampler) {
        samplers.put(name, sampler);
    }

    /**
     * Get classifier by name.
     * 
     * @param name Classifier name
     * @return Classifier function or default if not found
     */
    public Function<List<RolloutWithReward>, List<RolloutWithReward>[]> getClassifier(String name) {
        return classifiers.getOrDefault(name, classifiers.get("default_classify_rollouts"));
    }

    /**
     * Get validator by name.
     * 
     * @param name Validator name
     * @return Validator function or default if not found
     */
    public BiFunction<List<RolloutWithReward>, List<RolloutWithReward>, Boolean> getValidator(String name) {
        return validators.getOrDefault(name, validators.get("default_validate_stop"));
    }

    /**
     * Get sampler by name.
     * 
     * @param name Sampler name
     * @return Sampler function or default if not found
     */
    public TriFunction<List<RolloutWithReward>, List<RolloutWithReward>, Integer, List<RolloutWithReward>> getSampler(String name) {
        return samplers.getOrDefault(name, samplers.get("default_sampling"));
    }

    /**
     * Tri-function interface for sampler.
     */
    @FunctionalInterface
    public interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }
}