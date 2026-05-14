/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

/**
 * Configuration for spawned process management.
 * <p>
 * Mirrors Python's {@code SpawnConfig} in {@code runner/spawn/process_manager.py}.
 */
public class SpawnConfig {

    /** Interval in seconds between consecutive health checks. */
    private double healthCheckInterval = 5.0;

    /** Timeout in seconds to wait for a graceful shutdown before forcing termination. */
    private double shutdownTimeout = 10.0;

    /** Timeout in seconds for each individual health check response. */
    private double healthCheckTimeout = 3.0;

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
