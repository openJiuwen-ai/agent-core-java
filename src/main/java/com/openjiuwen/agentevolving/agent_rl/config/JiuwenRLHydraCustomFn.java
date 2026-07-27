/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config;

/**
 * Jiuwen RL Hydra custom function configuration.
 * <p>
 * Mirrors Python's {@code JiuwenRLHydraCustomFn} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/offline_config.py}.
 */
public class JiuwenRLHydraCustomFn {

    private String classifier = "default_classify_rollouts";
    private String validator = "default_validate_stop";
    private String sampler = "default_sampling";

    public String getClassifier() { return classifier; }
    public void setClassifier(String classifier) { this.classifier = classifier; }
    public String getValidator() { return validator; }
    public void setValidator(String validator) { this.validator = validator; }
    public String getSampler() { return sampler; }
    public void setSampler(String sampler) { this.sampler = sampler; }
}
