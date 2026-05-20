/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.onlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.onlineconfig.TrainingConfig.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrainingConfig {
    private String gpuIds = "4,5";
    private int threshold = 4;
    private int scanInterval = 30;
    private String ppoConfig;
    private String loraRepo;

    /**
     * Auto-generated for codecheck compliance.
     */
    public void validate() {
        VLLMServiceConfig.validateAtLeast(threshold, 1, "training.threshold");
        VLLMServiceConfig.validateAtLeast(scanInterval, 1, "training.scan_interval");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getGpu_ids() { return getGpuIds(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setGpu_ids(String value) { setGpuIds(value); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public int getScan_interval() { return getScanInterval(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setScan_interval(int value) { setScanInterval(value); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getPpo_config() { return getPpoConfig(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setPpo_config(String value) { setPpoConfig(value); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getLora_repo() { return getLoraRepo(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setLora_repo(String value) { setLoraRepo(value); }
}
