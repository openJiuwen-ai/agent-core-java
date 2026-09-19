/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.spawn;

/**
 * Public interface SpawnHandle used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public interface SpawnHandle {
    /**
     * isAlive.
     * 
     * @return the result
     * @since 0.1.7
     */
    boolean isAlive();

    /**
     * isHealthy.
     * 
     * @return the result
     * @since 0.1.7
     */
    boolean isHealthy();

    /**
     * startHealthCheck.
     * 
     * @param intervalMillis intervalMillis
     * @since 0.1.7
     */
    void startHealthCheck(Long intervalMillis);

    /**
     * stopHealthCheck.
     * 
     * @since 0.1.7
     */
    void stopHealthCheck();

    /**
     * shutdown.
     * 
     * @param timeoutMillis timeoutMillis
     * @return the result
     * @since 0.1.7
     */
    boolean shutdown(Long timeoutMillis);

    /**
     * forceKill.
     * 
     * @since 0.1.7
     */
    void forceKill();

    /**
     * waitForCompletion.
     * 
     * @return the result
     * @since 0.1.7
     */
    int waitForCompletion();

    /**
     * setOnUnhealthy.
     * 
     * @param onUnhealthy onUnhealthy
     * @since 0.1.7
     */
    void setOnUnhealthy(Runnable onUnhealthy);

    /**
     * isHealthCheckRunning.
     * 
     * @return the result
     * @since 0.1.7
     */
    default boolean isHealthCheckRunning() {
        return false;
    }
}
