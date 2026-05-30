/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.spawn;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * In-process spawn handle that mirrors SpawnedProcessHandle interface.
 * <p>
 * Lightweight handle wrapping a CompletableFuture/Thread instead of a subprocess.
 * Implements the same surface used by TeamAgent (_spawned_handles):
 * isAlive, isHealthy, shutdown, forceKill, start/stopHealthCheck.
 * <p>
 * Mirrors Python's {@code InProcessSpawnHandle} in
 * {@code openjiuwen.agent_teams.spawn.inprocess_handle}.
 */
public class InProcessHandle {

    private static final Logger logger = Logger.getLogger(InProcessHandle.class.getName());

    private final String processId;
    private CompletableFuture<?> task;
    private Consumer<Void> onUnhealthy;
    private final AtomicBoolean shutdownRequested;

    /**
     * Create a new InProcessHandle with auto-generated process ID.
     */
    public InProcessHandle() {
        this(UUID.randomUUID().toString().substring(0, 12));
    }

    /**
     * Create a new InProcessHandle with specified process ID.
     *
     * @param processId Process identifier
     */
    public InProcessHandle(String processId) {
        this.processId = processId;
        this.task = null;
        this.onUnhealthy = null;
        this.shutdownRequested = new AtomicBoolean(false);
    }

    /**
     * Create a new InProcessHandle with task.
     *
     * @param processId Process identifier
     * @param task      CompletableFuture wrapping the task
     */
    public InProcessHandle(String processId, CompletableFuture<?> task) {
        this.processId = processId;
        this.task = task;
        this.onUnhealthy = null;
        this.shutdownRequested = new AtomicBoolean(false);
    }

    // ------------------------------------------------------------------
    // Properties compatible with SpawnedProcessHandle
    // ------------------------------------------------------------------

    /**
     * Check if the wrapped task is still running.
     *
     * @return true if task exists and is not done
     */
    public boolean isAlive() {
        return task != null && !task.isDone();
    }

    /**
     * Check if the task is healthy (alive and not shutdown requested).
     *
     * @return true if alive and no shutdown requested
     */
    public boolean isHealthy() {
        return isAlive() && !shutdownRequested.get();
    }

    public String getProcessId() {
        return processId;
    }

    // ------------------------------------------------------------------
    // Health-check stubs (no-op for in-process tasks)
    // ------------------------------------------------------------------

    /**
     * No-op: in-process tasks do not need IPC health checks.
     *
     * @param interval ignored
     */
    public void startHealthCheck(double interval) {
        // No-op for in-process tasks
    }

    /**
     * No-op.
     */
    public void stopHealthCheck() {
        // No-op
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Wait for graceful task completion, then force cancel on timeout.
     *
     * @param timeout Timeout in seconds (default 10.0)
     * @return true if the task finished before timeout, false if force killed
     */
    public boolean shutdown(double timeout) {
        shutdownRequested.set(true);
        if (task == null || task.isDone()) {
            return true;
        }
        long timeoutMillis = Math.max(0L, Math.round(timeout * 1000));
        try {
            task.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            forceKill();
            return false;
        } catch (CancellationException | ExecutionException e) {
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            forceKill();
            return false;
        }
    }

    /**
     * Shutdown with default timeout.
     *
     * @return true if the task finished
     */
    public boolean shutdown() {
        return shutdown(10.0);
    }

    /**
     * Immediately cancel the task.
     */
    public void forceKill() {
        shutdownRequested.set(true);
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
    }

    /**
     * Block until the wrapped task finishes.
     *
     * @return 0 on success, -1 on failure or no task
     */
    public int waitForCompletion() {
        if (task == null) {
            return -1;
        }
        try {
            task.get();
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    /**
     * Set the wrapped task.
     *
     * @param task CompletableFuture to wrap
     */
    public void setTask(CompletableFuture<?> task) {
        this.task = task;
    }

    /**
     * Set the unhealthy callback.
     *
     * @param onUnhealthy Callback invoked when task becomes unhealthy
     */
    public void setOnUnhealthy(Consumer<Void> onUnhealthy) {
        this.onUnhealthy = onUnhealthy;
    }

    /**
     * Get shutdown requested status.
     */
    public boolean isShutdownRequested() {
        return shutdownRequested.get();
    }

    @Override
    public String toString() {
        return "InProcessHandle{processId='" + processId + "', isAlive=" + isAlive() + 
            ", isHealthy=" + isHealthy() + ", shutdownRequested=" + shutdownRequested.get() + "}";
    }
}
