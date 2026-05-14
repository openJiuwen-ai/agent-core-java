/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.optimizer;

import com.openjiuwen.agent_evolving.agent_rl.config.RLConfig;

/**
 * Minimal online RL optimizer shell.
 * <p>
 * Mirrors Python's {@code OnlineRLOptimizer} in
 * {@code openjiuwen.agent_evolving.agent_rl.optimizer.rl_optimizer}.
 */
public class OnlineRLOptimizer extends OfflineRLOptimizer {

    private String redisUrl;
    private double pollInterval = 10.0;
    private int minSamples = 1;

    public OnlineRLOptimizer(RLConfig config) {
        super(config);
    }

    public OnlineRLOptimizer setupGateway(String url, double pollInterval, int minSamples) {
        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IllegalArgumentException("setup_gateway() no longer accepts HTTP Gateway URLs; use setup_redis() instead via a redis:// URL");
        }
        this.redisUrl = url;
        this.pollInterval = pollInterval;
        this.minSamples = minSamples;
        return this;
    }

    public String getRedisUrl() { return redisUrl; }
    public double getPollInterval() { return pollInterval; }
    public int getMinSamples() { return minSamples; }
}
