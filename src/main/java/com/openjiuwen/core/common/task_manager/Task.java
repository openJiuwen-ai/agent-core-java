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
 * 
 * @since 0.1.7
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
        /**
         * handle.
         * 
         * @param task task
         * @param status status
         * @since 0.1.7
         */
        void handle(Task task, String status);
    }

    /**
     * Task.
     * 
     * @param owner owner
     * @param taskId taskId
     * @param name name
     * @param group group
     * @param timeout timeout
     * @param metadata metadata
     * @since 0.1.7
     */
    public Task(TaskManager owner, String taskId, String name, String group, Double timeout,
            Map<String, Object> metadata) {
        this.owner = owner;
        this.taskId = taskId;
        this.name = name;
        this.group = group;
        this.timeout = timeout;
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
    }

    /**
     * getTaskId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getName() {
        return name;
    }

    /**
     * getGroup.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getGroup() {
        return group;
    }

    /**
     * getParentTaskId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getParentTaskId() {
        return parentTaskId;
    }

    /**
     * setParentTaskId.
     * 
     * @param parentTaskId parentTaskId
     * @since 0.1.7
     */
    public void setParentTaskId(String parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    /**
     * getStatus.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TaskStatus getStatus() {
        return status;
    }

    /**
     * getTimeout.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Double getTimeout() {
        return timeout;
    }

    /**
     * getCreatedAt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * getStartedAt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Instant getStartedAt() {
        return startedAt;
    }

    /**
     * getFinishedAt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Instant getFinishedAt() {
        return finishedAt;
    }

    /**
     * getResult.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getResult() {
        return result;
    }

    /**
     * getException.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Throwable getException() {
        return exception;
    }

    /**
     * getMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getMetadata() {
        return new LinkedHashMap<>(metadata);
    }

    /**
     * getCancelledBy.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getCancelledBy() {
        return cancelledBy;
    }

    /**
     * getCancelReason.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getCancelReason() {
        return cancelReason;
    }

    /**
     * isTerminal.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isTerminal() {
        return TaskStatus.TERMINAL_STATES.contains(status);
    }

    /**
     * getDisplayName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDisplayName() {
        return name != null && !name.isBlank() ? name : taskId.substring(0, Math.min(taskId.length(), 8));
    }

    /**
     * getError.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getError() {
        return exception != null ? exception.getMessage() : null;
    }

    /**
     * setRunningFuture.
     * 
     * @param future future
     * @since 0.1.7
     */
    public synchronized void setRunningFuture(Future<?> future) {
        this.cancelHandle = future;
    }

    /**
     * setTimeoutHandle.
     * 
     * @param future future
     * @since 0.1.7
     */
    public synchronized void setTimeoutHandle(Future<?> future) {
        this.timeoutHandle = future;
    }

    /**
     * waitFor.
     * 
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
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
     * cancel.
     * 
     * @param isCascade isCascade
     * @param reason reason
     * @param cancelledBy cancelledBy
     * @return the result
     * @since 0.1.7
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
                owner.cascadeCancel(taskId, this.cancelReason);
            }
            if (timeoutHandle != null) {
                timeoutHandle.cancel(false);
            }
            return cancelHandle != null && cancelHandle.cancel(true);
        }
    }

    /**
     * abort.
     * 
     * @param reason reason
     * @return the result
     * @since 0.1.7
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
                } else {
                    // no-op
                }
            }
        } catch (CancellationException e) {
            synchronized (this) {
                if (isTimeoutTriggered) {
                    markTimeout();
                } else if (!isTerminal()) {
                    markCancelled();
                } else {
                    // no-op
                }
            }
        } catch (Exception e) {
            synchronized (this) {
                exception = e;
                status = TaskStatus.FAILED;
                finishedAt = Instant.now();
                doneFuture.completeExceptionally(e);
                log.info("Task execute failed: taskId={} name={} errorType={} message={}", taskId, name,
                        e.getClass().getSimpleName(), e.getMessage());
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

    /**
     * markCancelled.
     * 
     * @since 0.1.7
     */
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

    /**
     * markTimeout.
     * 
     * @since 0.1.7
     */
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

    /**
     * invokeCallback.
     * 
     * @param callback callback
     * @param statusName statusName
     * @since 0.1.7
     */
    private void invokeCallback(StatusCallback callback, String statusName) {
        if (callback != null && statusName != null) {
            callback.handle(this, statusName);
        } else if (owner != null) {
            if (status == TaskStatus.CANCELLED) {
                owner.handleStatus(this, "cancelled");
            } else if (status == TaskStatus.TIMEOUT) {
                owner.handleStatus(this, "timeout");
            }
        } else {
            // no-op
        }
    }

    /**
     * hashCode.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int hashCode() {
        return Objects.hash(taskId);
    }

    /**
     * equals.
     * 
     * @param other other
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Task task)) {
            return false;
        }
        return Objects.equals(taskId, task.taskId);
    }
}
