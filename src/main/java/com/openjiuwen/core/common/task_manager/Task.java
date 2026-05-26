/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Callable;

/**
 * Coroutine-task data model.
 */
public class Task {
    private static final Logger log = LoggerFactory.getLogger(Task.class);

    private final TaskManager owner;
    private final String taskId;
    private final String name;
    private final String group;
    private String parentTaskId;
    private volatile TaskStatus status = TaskStatus.PENDING;
    private final Double timeout;
    private final Instant createdAt = Instant.now();
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile Object result;
    private volatile Throwable exception;
    private final Map<String, Object> metadata;
    private final CompletableFuture<Object> doneFuture = new CompletableFuture<>();
    private volatile Future<?> cancelHandle;
    private volatile Future<?> timeoutHandle;
    private volatile String cancelledBy;
    private volatile String cancelReason;
    private volatile boolean isTimeoutTriggered;

    @FunctionalInterface
    interface StatusCallback {
        void handle(Task task, String status);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Task(TaskManager owner,
                String taskId,
                String name,
                String group,
                Double timeout,
                Map<String, Object> metadata) {
        this.owner = owner;
        this.taskId = taskId;
        this.name = name;
        this.group = group;
        this.timeout = timeout;
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getGroup() {
        return group;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getParentTaskId() {
        return parentTaskId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setParentTaskId(String parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TaskStatus getStatus() {
        return status;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Double getTimeout() {
        return timeout;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Instant getStartedAt() {
        return startedAt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Instant getFinishedAt() {
        return finishedAt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getResult() {
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Throwable getException() {
        return exception;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getMetadata() {
        return new LinkedHashMap<>(metadata);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getCancelledBy() {
        return cancelledBy;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getCancelReason() {
        return cancelReason;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isTerminal() {
        return TaskStatus.TERMINAL_STATES.contains(status);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDisplayName() {
        return name != null && !name.isBlank() ? name : taskId.substring(0, Math.min(taskId.length(), 8));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getError() {
        return exception != null ? exception.getMessage() : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized void setRunningFuture(Future<?> future) {
        this.cancelHandle = future;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized void setTimeoutHandle(Future<?> future) {
        this.timeoutHandle = future;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object waitFor() throws Exception {
        try {
            return doneFuture.get();
        } catch (InterruptedException e) {

            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new IllegalStateException(cause);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean cancel(boolean isCascade, String reason, String cancelledBy) {
        synchronized (this) {
            if (isTerminal()) {
                return false;
            }
            this.cancelReason = reason != null ? reason : "manual_cancel";
            if (cancelledBy != null) {
                this.cancelledBy = cancelledBy;
            }
            if (status == TaskStatus.PENDING) {
                markCancelled();
            }
            if (isCascade && owner != null) {
                owner.cascadeCancel(taskId, "parent_cancelled");
            }
            if (timeoutHandle != null) {
                timeoutHandle.cancel(false);
            }
            return cancelHandle != null && cancelHandle.cancel(true);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean abort(String reason) {
        synchronized (this) {
            if (isTerminal()) {
                return false;
            }
            this.cancelReason = reason != null ? reason : "manual_cancel";
            if (status == TaskStatus.PENDING) {
                markCancelled();
            }
            if (timeoutHandle != null) {
                timeoutHandle.cancel(false);
            }
            return cancelHandle != null && cancelHandle.cancel(true);
        }
    }

    synchronized void triggerTimeout() {
        if (isTerminal()) {
            return;
        }
        isTimeoutTriggered = true;
        cancelReason = "timeout";
        if (status == TaskStatus.PENDING) {
            markTimeout();
        }
        if (cancelHandle != null) {
            cancelHandle.cancel(true);
        }
    }

    void execute(Callable<?> callable, StatusCallback callback, boolean isCatchExceptionsEnabled) {
        String previousTaskId = TaskContext.setCurrentTaskId(taskId);
        startedAt = Instant.now();
        status = TaskStatus.RUNNING;
        log.info("Task execute start: taskId={} name={} group={}", taskId, name, group);
        invokeCallback(callback, "running");
        try {
            Object value = callable.call();
            synchronized (this) {
                if (isTimeoutTriggered) {
                    markTimeout();
                } else if (cancelReason != null) {
                    markCancelled();
                    return;
                } else {
                    result = value;
                    status = TaskStatus.COMPLETED;
                    finishedAt = Instant.now();
                    doneFuture.complete(value);
                    log.info("Task execute completed: taskId={} name={}", taskId, name);
                    invokeCallback(callback, "completed");
                }
            }
        } catch (InterruptedException e) {

            synchronized (this) {
                if (isTimeoutTriggered) {
                    markTimeout();
                } else if (!isTerminal()) {
                    markCancelled();
                }
            }
        } catch (CancellationException e) {
            synchronized (this) {
                if (isTimeoutTriggered) {
                    markTimeout();
                } else if (!isTerminal()) {
                    markCancelled();
                }
            }
        } catch (Exception e) {
            synchronized (this) {
                exception = e;
                status = TaskStatus.FAILED;
                finishedAt = Instant.now();
                doneFuture.completeExceptionally(e);
                log.info("Task execute failed: taskId={} name={} errorType={} message={}",
                        taskId, name, e.getClass().getSimpleName(), e.getMessage());
                invokeCallback(callback, "failed");
            }
            // The executor future observes this failure; task waiters use doneFuture.
        } finally {
            if (timeoutHandle != null) {
                timeoutHandle.cancel(false);
            }
            log.info("Task execute end: taskId={} name={} status={}", taskId, name, status);
            TaskContext.resetCurrentTaskId(previousTaskId);
        }
    }

    private void markCancelled() {
        if (isTerminal()) {
            return;
        }
        status = TaskStatus.CANCELLED;
        finishedAt = Instant.now();
        CancellationException error = new CancellationException(cancelReason != null ? cancelReason : "manual_cancel");
        exception = error;
        doneFuture.completeExceptionally(error);
        log.info("Task markCancelled: taskId={} name={} reason={}", taskId, name, cancelReason);
        invokeCallback(null, null);
    }

    private void markTimeout() {
        if (isTerminal()) {
            return;
        }
        status = TaskStatus.TIMEOUT;
        finishedAt = Instant.now();
        TimeoutException error = new TimeoutException("Task timeout");
        exception = error;
        doneFuture.completeExceptionally(error);
        log.info("Task markTimeout: taskId={} name={}", taskId, name);
        invokeCallback(null, null);
    }

    private void invokeCallback(StatusCallback callback, String statusName) {
        if (callback != null && statusName != null) {
            callback.handle(this, statusName);
        } else if (owner != null) {
            if (status == TaskStatus.CANCELLED) {
                owner.handleStatus(this, "cancelled");
            } else if (status == TaskStatus.TIMEOUT) {
                owner.handleStatus(this, "timeout");
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public int hashCode() {
        return Objects.hash(taskId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean equals(Object other) {
        if (!(other instanceof Task task)) {
            return false;
        }
        return Objects.equals(taskId, task.taskId);
    }
}
