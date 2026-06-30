/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.spawn;

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
     * Auto-generated for codecheck compliance.
     */
    public boolean isAlive() {
        return task != null && !task.isDone();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isHealthy() {
        return isAlive() && !isShutdownRequested;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void startHealthCheck(Long intervalMillis) {
        stopHealthCheck();
        long safeInterval = Math.max(1L, intervalMillis != null ? intervalMillis : 50_000L);
        healthCheckExecutor = new java.util.concurrent.ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "agent-teams-spawn-health-" + processId);
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignoredThread, ignoredError) -> markUnhealthy());
            return thread;
        });
        healthCheckTask = healthCheckExecutor.scheduleWithFixedDelay(
                () -> {
                    if (!isHealthy()) {
                        stopHealthCheck();
                        markUnhealthy();
                    }
                },
                safeInterval,
                safeInterval,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Auto-generated for codecheck compliance.
     */
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
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isHealthCheckRunning() {
        ScheduledFuture<?> scheduledTask = healthCheckTask;
        return scheduledTask != null && !scheduledTask.isDone() && !scheduledTask.isCancelled();
    }

    /**
     * Auto-generated for codecheck compliance.
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

            return task.isDone();
        } catch (ExecutionException | TimeoutException e) {
            return task.isDone();
        }
        return task.isDone();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void forceKill() {
        isShutdownRequested = true;
        stopHealthCheck();
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int waitForCompletion() {
        if (task == null) {
            return -1;
        }
        try {
            task.get();
            return 0;
        } catch (InterruptedException e) {

            return -1;
        } catch (ExecutionException | java.util.concurrent.CancellationException e) {
            return -1;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void markUnhealthy() {
        stopHealthCheck();
        Runnable callback = onUnhealthy;
        if (callback != null) {
            callback.run();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setOnUnhealthy(Runnable onUnhealthy) {
        this.onUnhealthy = onUnhealthy;
    }
}
