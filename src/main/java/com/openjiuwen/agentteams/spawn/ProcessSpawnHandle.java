/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.spawn;

import com.openjiuwen.core.runner.spawn.SpawnedProcessHandle;

import lombok.RequiredArgsConstructor;

/**
 * Public class ProcessSpawnHandle used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@RequiredArgsConstructor
public class ProcessSpawnHandle implements SpawnHandle {
    private final SpawnedProcessHandle delegate;

    /**
     * delegate.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SpawnedProcessHandle delegate() {
        return delegate;
    }

    /**
     * isAlive.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isAlive() {
        return delegate.isAlive();
    }

    /**
     * isHealthy.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isHealthy() {
        return delegate.isHealthy();
    }

    /**
     * startHealthCheck.
     * 
     * @param intervalMillis intervalMillis
     * @since 0.1.7
     */
    @Override
    public void startHealthCheck(Long intervalMillis) {
        if (intervalMillis == null) {
            delegate.startHealthCheck();
        } else {
            delegate.startHealthCheck(intervalMillis / 1_000.0);
        }
    }

    /**
     * stopHealthCheck.
     * 
     * @since 0.1.7
     */
    @Override
    public void stopHealthCheck() {
        delegate.stopHealthCheck();
    }

    /**
     * shutdown.
     * 
     * @param timeoutMillis timeoutMillis
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean shutdown(Long timeoutMillis) {
        return delegate.shutdown(timeoutMillis != null ? timeoutMillis / 1_000.0 : null);
    }

    /**
     * forceKill.
     * 
     * @since 0.1.7
     */
    @Override
    public void forceKill() {
        delegate.forceKill();
    }

    /**
     * waitForCompletion.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int waitForCompletion() {
        return delegate.waitForCompletion();
    }

    /**
     * setOnUnhealthy.
     * 
     * @param onUnhealthy onUnhealthy
     * @since 0.1.7
     */
    @Override
    public void setOnUnhealthy(Runnable onUnhealthy) {
        delegate.setOnUnhealthy(onUnhealthy);
    }

    /**
     * isHealthCheckRunning.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isHealthCheckRunning() {
        return delegate.isHealthCheckRunning();
    }
}
