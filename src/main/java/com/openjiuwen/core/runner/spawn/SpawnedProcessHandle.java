/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

/**
 * Handle for managing a spawned child process lifecycle.
 * <p>
 * Mirrors Python's {@code SpawnedProcessHandle} in {@code runner/spawn/process_manager.py}.
 * <p>
 * Provides methods for communication, health checking, and shutdown of spawned
 * child processes. When consecutive health check failures reach
 * {@code maxHealthFailures}, the optional {@code onUnhealthy} callback is invoked once.
 */
public class SpawnedProcessHandle {

    private final String processId;
    private final SpawnConfig config;
    private final Runnable onUnhealthy;
    private final int maxHealthFailures;

    private volatile boolean healthy = true;
    private volatile boolean shutdownRequested = false;
    private volatile int consecutiveFailures = 0;
    private volatile boolean unhealthyFired = false;

    /**
     * Create a new handle.
     *
     * @param processId         the unique process identifier
     * @param config            spawn configuration
     * @param onUnhealthy       callback invoked when the process becomes unhealthy (nullable)
     * @param maxHealthFailures number of consecutive failures before triggering onUnhealthy
     */
    public SpawnedProcessHandle(String processId, SpawnConfig config,
                                Runnable onUnhealthy, int maxHealthFailures) {
        this.processId = processId;
        this.config = config;
        this.onUnhealthy = onUnhealthy;
        this.maxHealthFailures = maxHealthFailures;
    }

    /**
     * Create a handle with default config and maxHealthFailures=2.
     *
     * @param processId the unique process identifier
     */
    public SpawnedProcessHandle(String processId) {
        this(processId, new SpawnConfig(), null, 2);
    }

    /**
     * Record a health check success, resetting consecutive failure count.
     */
    public void recordHealthSuccess() {
        this.healthy = true;
        this.consecutiveFailures = 0;
    }

    /**
     * Record a health check failure. If consecutive failures reach the threshold,
     * fire the onUnhealthy callback once.
     */
    public void recordHealthFailure() {
        this.consecutiveFailures++;
        if (consecutiveFailures >= maxHealthFailures) {
            this.healthy = false;
            if (!unhealthyFired && onUnhealthy != null) {
                unhealthyFired = true;
                onUnhealthy.run();
            }
        }
    }

    /**
     * Check if the process is considered healthy.
     *
     * @return true if healthy and not shutdown-requested
     */
    public boolean isHealthy() {
        return healthy && !shutdownRequested;
    }

    /**
     * Mark the process as shutdown-requested.
     */
    public void requestShutdown() {
        this.shutdownRequested = true;
    }

    public String getProcessId() {
        return processId;
    }

    public SpawnConfig getConfig() {
        return config;
    }

    public boolean isShutdownRequested() {
        return shutdownRequested;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }
}
