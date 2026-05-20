/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.spawn;

/**
 * Public interface SpawnHandle used by the Java parity implementation.
 *
 * @since 1.0
 */
public interface SpawnHandle {
    boolean isAlive();

    boolean isHealthy();

    void startHealthCheck(Long intervalMillis);

    void stopHealthCheck();

    boolean shutdown(Long timeoutMillis);

    void forceKill();

    int waitForCompletion();

    void setOnUnhealthy(Runnable onUnhealthy);

    default boolean isHealthCheckRunning() {
        return false;
    }
}
