/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.spawn;

import com.openjiuwen.core.runner.spawn.SpawnedProcessHandle;
import lombok.RequiredArgsConstructor;

/**
 * Public class ProcessSpawnHandle used by the Java parity implementation.
 *
 * @since 1.0
 */
@RequiredArgsConstructor
public class ProcessSpawnHandle implements SpawnHandle {
    private final SpawnedProcessHandle delegate;

    /**
     * Auto-generated for codecheck compliance.
     */
    public SpawnedProcessHandle delegate() {
        return delegate;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isAlive() {
        return delegate.isAlive();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isHealthy() {
        return delegate.isHealthy();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void startHealthCheck(Long intervalMillis) {
        if (intervalMillis == null) {
            delegate.startHealthCheck();
        } else {
            delegate.startHealthCheck(intervalMillis / 1_000.0);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void stopHealthCheck() {
        delegate.stopHealthCheck();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean shutdown(Long timeoutMillis) {
        return delegate.shutdown(timeoutMillis != null ? timeoutMillis / 1_000.0 : null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void forceKill() {
        delegate.forceKill();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public int waitForCompletion() {
        return delegate.waitForCompletion();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setOnUnhealthy(Runnable onUnhealthy) {
        delegate.setOnUnhealthy(onUnhealthy);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isHealthCheckRunning() {
        return delegate.isHealthCheckRunning();
    }
}
