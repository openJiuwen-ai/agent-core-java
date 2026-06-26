/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import java.util.Map;

/**
 * Registry for rollout processors that manages classifiers, validators, and samplers.
 *
 * <p>Mirrors Python's {@code ProcessorsRegistry} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/coordinator/processors.py}.</p>
 */
public class ProcessorsRegistry {

    private final RolloutProcessors.ProcessorsRegistry delegate = new RolloutProcessors.ProcessorsRegistry();

    public RolloutProcessors.ClassifierProcessor registerClassifier(
            String name,
            RolloutProcessors.ClassifierProcessor classifier) {
        return delegate.registerClassifier(name, classifier);
    }

    public RolloutProcessors.ValidatorProcessor registerValidator(
            String name,
            RolloutProcessors.ValidatorProcessor validator) {
        return delegate.registerValidator(name, validator);
    }

    public RolloutProcessors.SamplerProcessor registerSampler(
            String name,
            RolloutProcessors.SamplerProcessor sampler) {
        return delegate.registerSampler(name, sampler);
    }

    public RolloutProcessors.ClassifierProcessor getClassifier(String name) {
        return delegate.getClassifier(name);
    }

    public RolloutProcessors.ValidatorProcessor getValidator(String name) {
        return delegate.getValidator(name);
    }

    public RolloutProcessors.SamplerProcessor getSampler(String name) {
        return delegate.getSampler(name);
    }

    public Map<String, RolloutProcessors.ClassifierProcessor> classifiers() {
        return delegate.classifiers();
    }

    public Map<String, RolloutProcessors.ValidatorProcessor> validators() {
        return delegate.validators();
    }

    public Map<String, RolloutProcessors.SamplerProcessor> samplers() {
        return delegate.samplers();
    }
}
