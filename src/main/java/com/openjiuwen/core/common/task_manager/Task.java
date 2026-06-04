/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;

/**
 * Coroutine task data model.
 * <p>
 * Mirrors Python's {@code Task} dataclass from
 * <code>common/task_manager/task.py</code>.
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
    private Exception exception;
    private Map<String, Object> metadata = new HashMap<>();

    private String cancelledBy;
    private String cancelReason;

    private final CompletableFuture<Object> doneFuture = new CompletableFuture<>();
    private volatile CompletableFuture<?> executionFuture;

    public Task(String taskId) {
        this.taskId = taskId;
    }

    public Task(String taskId, String name, String group) {
        this.taskId = taskId;
        this.name = name;
        this.group = group;
    }

    public Task(String taskId, String name, String group, Double timeout, Map<String, Object> metadata) {
        this.taskId = taskId;
        this.name = name;
        this.group = group;
        this.timeout = timeout;
        if (metadata != null) {
            this.metadata = metadata;
        }
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public String getDisplayName() {
        return name != null ? name : taskId.substring(0, Math.min(8, taskId.length()));
    }

    public String getError() {
        return exception != null ? exception.getMessage() : null;
    }

    public void setError(String error) {
        this.exception = new RuntimeException(error);
    }

    public void complete(Object result) {
        if (isTerminal()) {
            return;
        }
        this.result = result;
        this.status = TaskStatus.COMPLETED;
        this.finishedAt = Instant.now();
        doneFuture.complete(result);
    }

    public void fail(Exception exception) {
        if (isTerminal()) {
            return;
        }
        this.exception = exception;
        this.status = TaskStatus.FAILED;
        this.finishedAt = Instant.now();
        doneFuture.completeExceptionally(exception);
    }

    public void cancel(String cancelledBy, String reason) {
        if (isTerminal()) {
            return;
        }
        this.status = TaskStatus.CANCELLED;
        this.cancelledBy = cancelledBy;
        this.cancelReason = reason != null ? reason : "manual_cancel";
        this.finishedAt = Instant.now();
        doneFuture.cancel(false);
    }

    public boolean cancel() {
        return cancel(false);
    }

    public boolean cancel(boolean cascade) {
        return cancel(cascade, null, null);
    }

    public boolean cancel(boolean cascade, String reason) {
        return cancel(cascade, reason, null);
    }

    public boolean cancel(boolean cascade, String reason, String cancelledBy) {
        if (isTerminal()) {
            return false;
        }
        TaskManager.getInstance().cancelTask(taskId, reason != null ? reason : "manual_cancel");
        if (cancelledBy != null) {
            this.cancelledBy = cancelledBy;
        }
        if (cascade) {
            TaskManager.getInstance().cascadeCancel(taskId, "parent_cancelled");
        }
        return true;
    }

    public Object waitForResult() throws Exception {
        try {
            return doneFuture.get();
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(cause);
        }
    }

    public CompletableFuture<Object> getDoneFuture() {
        return doneFuture;
    }
    
    /**
     * Alias for getDoneFuture() for compatibility.
     */
    public CompletableFuture<?> getFuture() {
        CompletableFuture<?> future = executionFuture;
        return future != null ? future : doneFuture;
    }
    
    /**
     * Set future - for compatibility with older code.
     */
    public void setFuture(CompletableFuture<?> future) {
        this.executionFuture = future;
        if (future == null) {
            return;
        }
        future.whenComplete((value, throwable) -> {
            if (throwable == null) {
                doneFuture.complete(value);
            } else if (throwable instanceof CancellationException || future.isCancelled()) {
                doneFuture.cancel(false);
            } else {
                doneFuture.completeExceptionally(unwrapCompletionException(throwable));
            }
        });
    }

    private static Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof java.util.concurrent.CompletionException completionException
                && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }
    
    /**
     * Set cancelledBy - for compatibility.
     */
    public void setCancelledBy(String cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    // Getters and setters
    public String getTaskId() { return taskId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public String getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(String parentTaskId) { this.parentTaskId = parentTaskId; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Double getTimeout() { return timeout; }
    public void setTimeout(Double timeout) { this.timeout = timeout; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
    public Exception getException() { return exception; }
    public void setException(Exception exception) { this.exception = exception; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public String getCancelledBy() { return cancelledBy; }
    public String getCancelReason() { return cancelReason; }

    @Override
    public int hashCode() { return taskId.hashCode(); }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Task)) return false;
        return taskId.equals(((Task) obj).taskId);
    }
}
