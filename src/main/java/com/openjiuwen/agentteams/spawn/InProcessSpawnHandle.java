/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.spawn;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;

import lombok.Builder;
import lombok.Getter;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Lightweight in-process spawn handle mirroring the first Python lifecycle surface.
 * 
 * @since 0.1.7
 */
@Getter
@Builder(toBuilder = true)
public class InProcessSpawnHandle implements SpawnHandle {
    private final String processId;
    private final Future<?> task;
    @Builder.Default
    private Runnable onUnhealthy = null;
    @Builder.Default
    private volatile boolean isShutdownRequested = false;
    private volatile ScheduledExecutorService healthCheckExecutor;
    private volatile ScheduledFuture<?> healthCheckTask;

    /**
     * isAlive.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isAlive() {
        return task != null && !task.isDone();
    }

    /**
     * isHealthy.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isHealthy() {
        return isAlive() && !isShutdownRequested;
    }

    /**
     * startHealthCheck.
     * 
     * @param intervalMillis intervalMillis
     * @since 0.1.7
     */
    public void startHealthCheck(Long intervalMillis) {
        stopHealthCheck();
        long safeInterval = Math.max(1L, intervalMillis != null ? intervalMillis : 50_000L);
        healthCheckExecutor = OpenJiuwenExecutors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "agent-teams-spawn-health-" + processId);
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignoredThread, ignoredError) -> markUnhealthy());
            return thread;
        });
        healthCheckTask = healthCheckExecutor.scheduleWithFixedDelay(() -> {
            if (!isHealthy()) {
                stopHealthCheck();
                markUnhealthy();
            }
        }, safeInterval, safeInterval, TimeUnit.MILLISECONDS);
    }

    /**
     * stopHealthCheck.
     * 
     * @since 0.1.7
     */
    @Override
    public void stopHealthCheck() {
        ScheduledFuture<?> scheduledTask = healthCheckTask;
        healthCheckTask = null;
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
        }
        ScheduledExecutorService executor = healthCheckExecutor;
        healthCheckExecutor = null;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * isHealthCheckRunning.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isHealthCheckRunning() {
        ScheduledFuture<?> scheduledTask = healthCheckTask;
        return scheduledTask != null && !scheduledTask.isDone() && !scheduledTask.isCancelled();
    }

    /**
     * shutdown.
     * 
     * @param timeoutMillis timeoutMillis
     * @return the result
     * @since 0.1.7
     */
    public boolean shutdown(Long timeoutMillis) {
        isShutdownRequested = true;
        stopHealthCheck();
        if (task == null || task.isDone()) {
            return true;
        }
        task.cancel(true);
        try {
            task.get(timeoutMillis != null ? timeoutMillis : 10_000L, TimeUnit.MILLISECONDS);
        } catch (CancellationException ignored) {
            return task.isDone();
        } catch (InterruptedException e) {
            // do not self-interrupt (G.CON.10)
            return task.isDone();
        } catch (ExecutionException | TimeoutException e) {
            return task.isDone();
        }
        return task.isDone();
    }

    /**
     * forceKill.
     * 
     * @since 0.1.7
     */
    @Override
    public void forceKill() {
        isShutdownRequested = true;
        stopHealthCheck();
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
    }

    /**
     * waitForCompletion.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int waitForCompletion() {
        if (task == null) {
            return -1;
        }
        try {
            task.get();
            return 0;
        } catch (InterruptedException e) {
            // do not self-interrupt (G.CON.10)
            return -1;
        } catch (ExecutionException | java.util.concurrent.CancellationException e) {
            return -1;
        }
    }

    /**
     * markUnhealthy.
     * 
     * @since 0.1.7
     */
    public void markUnhealthy() {
        stopHealthCheck();
        Runnable callback = onUnhealthy;
        if (callback != null) {
            callback.run();
        }
    }

    /**
     * setOnUnhealthy.
     * 
     * @param onUnhealthy onUnhealthy
     * @since 0.1.7
     */
    public void setOnUnhealthy(Runnable onUnhealthy) {
        this.onUnhealthy = onUnhealthy;
    }
}
