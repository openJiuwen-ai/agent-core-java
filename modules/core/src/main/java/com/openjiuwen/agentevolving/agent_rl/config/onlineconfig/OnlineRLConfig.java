/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.onlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.onlineconfig.OnlineRLConfig.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OnlineRLConfig {
    private boolean isDemo = false;

    /**
     * VLLMServiceConfig.
     * 
     * @since 0.1.7
     */
    private VLLMServiceConfig inference = new VLLMServiceConfig();

    /**
     * JudgeConfig.
     * 
     * @since 0.1.7
     */
    private JudgeConfig judge = new JudgeConfig();

    /**
     * GatewayServiceConfig.
     * 
     * @since 0.1.7
     */
    private GatewayServiceConfig gateway = new GatewayServiceConfig();

    /**
     * TrajectoryConfig.
     * 
     * @since 0.1.7
     */
    private TrajectoryConfig trajectory = new TrajectoryConfig();

    /**
     * TrainingConfig.
     * 
     * @since 0.1.7
     */
    private TrainingConfig training = new TrainingConfig();

    /**
     * JiuwenConfig.
     * 
     * @since 0.1.7
     */
    private JiuwenConfig jiuwen = new JiuwenConfig();

    /**
     * syncAndValidateLaunch.
     * 
     * @return the result
     * @since 0.1.7
     */
    public OnlineRLConfig syncAndValidateLaunch() {
        inference = Objects.requireNonNull(inference, "inference is required");
        judge = Objects.requireNonNull(judge, "judge is required");
        gateway = Objects.requireNonNull(gateway, "gateway is required");
        trajectory = Objects.requireNonNull(trajectory, "trajectory is required");
        training = Objects.requireNonNull(training, "training is required");
        jiuwen = Objects.requireNonNull(jiuwen, "jiuwen is required");

        inference.validate("inference");
        judge.validate("judge");
        gateway.validate();
        trajectory.validate();
        training.validate();
        jiuwen.validate();

        if (judge.isReuseInferenceIfSameModel()) {
            judge.setModelPath(inference.getModelPath());
            judge.setModelName(inference.getModelName());
        }
        if (inference.getExistingUrl() == null && inference.getPort() == null) {
            throw new IllegalArgumentException(
                    "inference.port is required when inference.existing_url is not set (set via YAML or e.g."
                            + " --vllm-port).");
        }
        if (judge.getExistingUrl() == null && judge.getPort() == null) {
            throw new IllegalArgumentException(
                    "judge.port is required when judge.existing_url is not set (YAML or e.g. --judge-port).");
        }
        if (gateway.getPort() == null) {
            throw new IllegalArgumentException("gateway.port is required (--gateway-port or YAML).");
        }
        if (gateway.getRedisUrl() == null || gateway.getRedisUrl().isBlank()) {
            throw new IllegalArgumentException("gateway.redis_url is required (--redis-url or YAML).");
        }
        if (jiuwen.isEnabled()) {
            if (jiuwen.getAgentServerPort() == null) {
                throw new IllegalArgumentException("jiuwen.agent_server_port is required when jiuwen.enabled is true.");
            }
            if (jiuwen.getWsPort() == null) {
                throw new IllegalArgumentException("jiuwen.ws_port is required when jiuwen.enabled is true.");
            }
            if (jiuwen.getWebPort() == null) {
                throw new IllegalArgumentException("jiuwen.web_port is required when jiuwen.enabled is true.");
            }
        }
        return this;
    }
}
