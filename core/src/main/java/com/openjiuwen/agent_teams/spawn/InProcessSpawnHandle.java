/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.spawn;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * In-process spawn handle that mirrors subprocess-handle lifecycle helpers.
 *
 * <p>Mirrors Python's {@code InProcessSpawnHandle} in
 * {@code openjiuwen/agent_teams/spawn/inprocess_handle.py}.</p>
 */
public class InProcessSpawnHandle {

    private final String processId;
    private Future<?> task;
    private Runnable onUnhealthy;
    private boolean shutdownRequested;
    private Object agentRef;
    private Object chunkForward;

    public InProcessSpawnHandle() {
        this(generateProcessId(), null);
    }

    public InProcessSpawnHandle(String processId) {
        this(processId, null);
    }

    public InProcessSpawnHandle(String processId, Future<?> task) {
        this.processId = processId;
        this.task = task;
    }

    private static String generateProcessId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public String getProcessId() {
        return processId;
    }

    public Future<?> getTask() {
        return task;
    }

    public void setTask(Future<?> task) {
        this.task = task;
    }

    public Runnable getOnUnhealthy() {
        return onUnhealthy;
    }

    public void setOnUnhealthy(Runnable onUnhealthy) {
        this.onUnhealthy = onUnhealthy;
    }

    public boolean isShutdownRequested() {
        return shutdownRequested;
    }

    public Object getAgentRef() {
        return agentRef;
    }

    public void setAgentRef(Object agentRef) {
        this.agentRef = agentRef;
    }

    public Object getChunkForward() {
        return chunkForward;
    }

    public void setChunkForward(Object chunkForward) {
        this.chunkForward = chunkForward;
    }

    public boolean isAlive() {
        return task != null && !task.isDone();
    }

    /**
     * Terminated tasks are healthy when they finished without exception and shutdown was not requested.
     * A normal completion (after shutdown_member/clean_team) must not look unhealthy and trigger respawn.
     */
    public boolean isHealthy() {
        if (shutdownRequested) {
            return false;
        }
        if (task == null) {
            return false;
        }
        if (!task.isDone()) {
            return true;
        }
        try {
            task.get();
            return true;
        } catch (ExecutionException | CancellationException e) {
            return false;
        } catch (InterruptedException e) {
            return false;
        }
    }

    public CompletableFuture<Void> startHealthCheck(Double interval) {
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> stopHealthCheck() {
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Boolean> shutdown(Double timeoutSeconds) {
        shutdownRequested = true;
        if (task == null || task.isDone()) {
            return CompletableFuture.completedFuture(true);
        }
        task.cancel(true);
        long timeoutMillis = Math.max(1L, Math.round(((timeoutSeconds == null ? 10.0 : timeoutSeconds) * 1000)));
        try {
            task.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (CancellationException | ExecutionException | TimeoutException ignored) {
            // Python suppresses cancellation/timeout while only caring whether the task is done.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return CompletableFuture.completedFuture(task.isDone());
    }

    public CompletableFuture<Boolean> shutdown() {
        return shutdown(10.0);
    }

    public CompletableFuture<Void> forceKill() {
        shutdownRequested = true;
        if (task != null && !task.isDone()) {
            task.cancel(true);
            try {
                task.get();
            } catch (CancellationException | ExecutionException ignored) {
                // Python suppresses the cancellation result.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Integer> waitForCompletion() {
        if (task == null) {
            return CompletableFuture.completedFuture(-1);
        }
        try {
            task.get();
            return CompletableFuture.completedFuture(0);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return CompletableFuture.completedFuture(-1);
        } catch (CancellationException | ExecutionException ignored) {
            return CompletableFuture.completedFuture(-1);
        }
    }
}
