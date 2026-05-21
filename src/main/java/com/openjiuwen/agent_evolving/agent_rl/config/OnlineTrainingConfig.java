/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Training configuration for online RL (different from offline TrainingConfig).
 * <p>
 * Mirrors Python's {@code TrainingConfig} in
 * {@code openjiuwen.agent_evolving.agent_rl.config.online_config}.
 */
public class OnlineTrainingConfig {

    private String gpuIds = "4,5";
    private int threshold = 4;
    private int scanInterval = 30;
    private String ppoConfig;
    private String loraRepo;

    public String getGpuIds() { return gpuIds; }
    public void setGpuIds(String gpuIds) { this.gpuIds = gpuIds; }
    public int getThreshold() { return threshold; }
    public void setThreshold(int threshold) { 
        if (threshold < 1) throw new IllegalArgumentException("threshold must be >= 1");
        this.threshold = threshold; 
    }
    public int getScanInterval() { return scanInterval; }
    public void setScanInterval(int scanInterval) { 
        if (scanInterval < 1) throw new IllegalArgumentException("scanInterval must be >= 1");
        this.scanInterval = scanInterval; 
    }
    public String getPpoConfig() { return ppoConfig; }
    public void setPpoConfig(String ppoConfig) { this.ppoConfig = ppoConfig; }
    public String getLoraRepo() { return loraRepo; }
    public void setLoraRepo(String loraRepo) { this.loraRepo = loraRepo; }
}