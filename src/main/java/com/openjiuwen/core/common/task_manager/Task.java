/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Coroutine task data model.
 *
 * <p>Mirrors Python's {@code Task} in
 * {@code openjiuwen/core/common/task_manager/task.py}.</p>
 */
public class Task {

    private final String taskId;
    private String name;
    private String group;
    private String parentTaskId;
    private TaskStatus status = TaskStatus.PENDING;
    private Double timeout;
    private Instant createdAt = Instant.now();
    private Instant startedAt;
    private Instant finishedAt;
    private Object result;
    private Throwable exception;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private String cancelledBy;
    private String cancelReason;
    private final CompletableFuture<Object> doneFuture = new CompletableFuture<>();
    private volatile CompletableFuture<?> executionFuture;

    public Task(String taskId) {
        this(taskId, null, null, null, Map.of());
    }

    public Task(String taskId, String name, String group, Double timeout, Map<String, Object> metadata) {
        this.taskId = Objects.requireNonNull(taskId, "taskId must not be null");
        this.name = name;
        this.group = group;
        this.timeout = timeout;
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    public String getDisplayName() {
        return name == null || name.isBlank() ? taskId.substring(0, Math.min(8, taskId.length())) : name;
    }

    public String getError() {
        return exception == null ? null : exception.getMessage();
    }

    public CompletableFuture<Object> waitForCompletion() {
        return doneFuture;
    }

    public CompletableFuture<Object> waitResult() {
        return waitForCompletion();
    }

    public Object waitForResult() throws Exception {
        try {
            return doneFuture.get();
        } catch (java.util.concurrent.ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checkedException) {
                throw checkedException;
            }
            throw new RuntimeException(cause);
        }
    }

    public boolean cancel() {
        return cancel(false, "manual_cancel", null);
    }

    public boolean cancel(boolean cascade, String reason, String cancelledBy) {
        if (isTerminal()) {
            return false;
        }
        TaskManager.getInstance().cancelTask(taskId, reason == null ? "manual_cancel" : reason, cancelledBy);
        if (cascade) {
            TaskManager.getInstance().cascadeCancel(taskId, "parent_cancelled");
        }
        return true;
    }

    public boolean abort(String reason) {
        return markCancelled(reason == null ? "manual_cancel" : reason, cancelledBy);
    }

    void start() {
        status = TaskStatus.RUNNING;
        startedAt = Instant.now();
    }

    void complete(Object value) {
        if (isTerminal()) {
            return;
        }
        result = value;
        status = TaskStatus.COMPLETED;
        finishedAt = Instant.now();
        doneFuture.complete(value);
    }

    void fail(Throwable throwable) {
        if (isTerminal()) {
            return;
        }
        exception = throwable;
        status = TaskStatus.FAILED;
        finishedAt = Instant.now();
        doneFuture.completeExceptionally(throwable);
    }

    boolean markCancelled(String reason, String byTaskId) {
        if (isTerminal()) {
            return false;
        }
        status = TaskStatus.CANCELLED;
        cancelReason = reason == null ? "manual_cancel" : reason;
        cancelledBy = byTaskId;
        finishedAt = Instant.now();
        if (!doneFuture.isDone()) {
            doneFuture.completeExceptionally(new CancellationException(cancelReason));
        }
        return true;
    }

    void markTimeout() {
        if (isTerminal()) {
            return;
        }
        status = TaskStatus.TIMEOUT;
        exception = new java.util.concurrent.TimeoutException("Task timeout");
        finishedAt = Instant.now();
        doneFuture.completeExceptionally(exception);
    }

    void setExecutionFuture(CompletableFuture<?> future) {
        executionFuture = future;
    }

    public void setFuture(CompletableFuture<?> future) {
        setExecutionFuture(future);
    }

    static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getName() {
        return name;
    }

    public String getGroup() {
        return group;
    }

    public String getParentTaskId() {
        return parentTaskId;
    }

    public void setParentTaskId(String parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Double getTimeout() {
        return timeout;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Object getResult() {
        return result;
    }

    public Throwable getException() {
        return exception;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(String cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public CompletableFuture<?> getExecutionFuture() {
        return executionFuture;
    }

    public CompletableFuture<?> getFuture() {
        return executionFuture == null ? doneFuture : executionFuture;
    }

    @Override
    public int hashCode() {
        return taskId.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Task task && taskId.equals(task.taskId);
    }
}
