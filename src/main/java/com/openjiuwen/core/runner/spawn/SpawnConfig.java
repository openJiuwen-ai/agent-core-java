/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

/**
 * Configuration for spawned process management.
 *
 * <p>Mirrors Python's {@code SpawnConfig} in
 * {@code openjiuwen/core/runner/spawn/process_manager.py}.</p>
 */
public class SpawnConfig {

    private double healthCheckInterval = 5.0D;
    private double shutdownTimeout = 10.0D;
    private double healthCheckTimeout = 3.0D;

    public SpawnConfig() {
    }

    public SpawnConfig(double healthCheckInterval, double shutdownTimeout, double healthCheckTimeout) {
        this.healthCheckInterval = healthCheckInterval;
        this.shutdownTimeout = shutdownTimeout;
        this.healthCheckTimeout = healthCheckTimeout;
    }

    public double getHealthCheckInterval() {
        return healthCheckInterval;
    }

    public void setHealthCheckInterval(double healthCheckInterval) {
        this.healthCheckInterval = healthCheckInterval;
    }

    public double getShutdownTimeout() {
        return shutdownTimeout;
    }

    public void setShutdownTimeout(double shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    public double getHealthCheckTimeout() {
        return healthCheckTimeout;
    }

    public void setHealthCheckTimeout(double healthCheckTimeout) {
        this.healthCheckTimeout = healthCheckTimeout;
    }
}
