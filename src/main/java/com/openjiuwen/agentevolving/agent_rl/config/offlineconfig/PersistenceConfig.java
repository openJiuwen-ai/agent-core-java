/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.PersistenceConfig.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PersistenceConfig {
    private boolean isEnabled = false;
    private String savePath;
    private int flushInterval = 100;
    private boolean isSaveRollouts = true;
    private boolean isSaveStepSummaries = true;

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getFlush_interval() { return getFlushInterval(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isSave_rollouts() { return isSaveRollouts(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isSave_step_summaries() { return isSaveStepSummaries(); }
}
