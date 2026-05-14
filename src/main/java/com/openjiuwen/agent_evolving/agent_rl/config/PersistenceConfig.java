/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

/**
 * Rollout persistence configuration.
 * <p>
 * Mirrors Python's {@code PersistenceConfig} in
 * {@code openjiuwen.agent_evolving.agent_rl.config.offline_config}.
 */
public class PersistenceConfig {

    private boolean enabled;
    private String savePath;
    private int flushInterval = 100;
    private boolean saveRollouts = true;
    private boolean saveStepSummaries = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSavePath() { return savePath; }
    public void setSavePath(String savePath) { this.savePath = savePath; }
    public int getFlushInterval() { return flushInterval; }
    public void setFlushInterval(int flushInterval) { this.flushInterval = flushInterval; }
    public boolean isSaveRollouts() { return saveRollouts; }
    public void setSaveRollouts(boolean saveRollouts) { this.saveRollouts = saveRollouts; }
    public boolean isSaveStepSummaries() { return saveStepSummaries; }
    public void setSaveStepSummaries(boolean saveStepSummaries) { this.saveStepSummaries = saveStepSummaries; }
}
