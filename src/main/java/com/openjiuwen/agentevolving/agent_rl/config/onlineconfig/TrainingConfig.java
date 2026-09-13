/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.onlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.onlineconfig.TrainingConfig.
 * 
 * @since 0.1.7
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
     * validate.
     * 
     * @since 0.1.7
     */
    public void validate() {
        VLLMServiceConfig.validateAtLeast(threshold, 1, "training.threshold");
        VLLMServiceConfig.validateAtLeast(scanInterval, 1, "training.scan_interval");
    }

    /**
     * getGpu_ids.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getGpu_ids() {
        return getGpuIds();
    }

    /**
     * setGpu_ids.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setGpu_ids(String value) {
        setGpuIds(value);
    }

    /**
     * getScan_interval.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getScan_interval() {
        return getScanInterval();
    }

    /**
     * setScan_interval.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setScan_interval(int value) {
        setScanInterval(value);
    }

    /**
     * getPpo_config.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getPpo_config() {
        return getPpoConfig();
    }

    /**
     * setPpo_config.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setPpo_config(String value) {
        setPpoConfig(value);
    }

    /**
     * getLora_repo.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getLora_repo() {
        return getLoraRepo();
    }

    /**
     * setLora_repo.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setLora_repo(String value) {
        setLoraRepo(value);
    }
}
