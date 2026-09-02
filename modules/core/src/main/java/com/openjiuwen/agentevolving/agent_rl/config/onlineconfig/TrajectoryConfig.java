/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.onlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.onlineconfig.TrajectoryConfig.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrajectoryConfig {
    private int batchSize = 4;
    private String mode = "feedback_level";

    /**
     * validate.
     * 
     * @since 0.1.7
     */
    public void validate() {
        VLLMServiceConfig.validateAtLeast(batchSize, 1, "trajectory.batch_size");
    }

    /**
     * getBatch_size.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getBatch_size() {
        return getBatchSize();
    }

    /**
     * setBatch_size.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setBatch_size(int value) {
        setBatchSize(value);
    }
}
