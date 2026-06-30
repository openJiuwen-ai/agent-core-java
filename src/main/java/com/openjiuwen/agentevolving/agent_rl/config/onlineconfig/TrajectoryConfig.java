/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.onlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.onlineconfig.TrajectoryConfig.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrajectoryConfig {
    private int batchSize = 4;
    private String mode = "feedback_level";

    /**
     * Auto-generated for codecheck compliance.
     */
    public void validate() {
        VLLMServiceConfig.validateAtLeast(batchSize, 1, "trajectory.batch_size");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getBatch_size() {
        return getBatchSize();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setBatch_size(int value) {
        setBatchSize(value);
    }
}
