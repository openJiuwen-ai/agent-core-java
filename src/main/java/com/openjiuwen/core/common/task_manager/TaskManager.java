/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import com.openjiuwen.core.common.VirtualThreadSupport;

import com.openjiuwen.core.runner.callback.TaskManagerEvents;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Coroutine task manager singleton.
 *
 * <p>Mirrors Python's {@code TaskManager} and module-level helpers in
 * {@code openjiuwen/core/common/task_manager/manager.py}.</p>
 */
public class TaskManager {

    private static final Logger LOGGER = Logger.getLogger(TaskManager.class.getName());
    private static final Object INSTANCE_LOCK = new Object();
    private static volatile TaskManager instance;

    private final TaskRegistry registry = new TaskRegistry();
    private final Object lock = new Object();
    private final ExecutorService executorService;
    private final ScheduledExecutorService timeoutScheduler;
    private final Map<String, CopyOnWriteArrayList<Consumer<Task>>> callbacks = new LinkedHashMap<>();

    public TaskManager() {
        this(VirtualThreadSupport.newThreadPerTaskExecutor(), Executors.newSingleThreadScheduledExecutor());
    }

    TaskManager(ExecutorService executorService, ScheduledExecutorService timeoutScheduler) {
        this.executorService = Objects.requireNonNull(executorService, "executorService");
        this.timeoutScheduler = Objects.requireNonNull(timeoutScheduler, "timeoutScheduler");
        LOGGER.info("CoroutineTaskManager initialized with explicit registry cleanup");
    }

    public static TaskManager getInstance() {
        TaskManager current = instance;
        if (current == null) {
            synchronized (INSTANCE_LOCK) {
                current = instance;
                if (current == null) {
                    current = new TaskManager();
                    instance = current;
                }
            }
        }
        return current;
    }

    public static TaskManager getTaskManager() {
        return getInstance();
    }

    public static void resetInstance() {
        synchronized (INSTANCE_LOCK) {
            if (instance != null) {
                instance.shutdown();
                instance = null;
            }
        }
    }

    public Task createTask(Callable<?> callable) {
        return createTask(callable, null, null, null, null, Map.of(), false);
    }

    public Task createTask(Callable<?> callable,
                           String taskId,
                           String name,
                           String group,
                           Double timeout,
                           Map<String, Object> metadata,
                           boolean catchExceptions) {
        Objects.requireNonNull(callable, "callable");
        String actualTaskId = taskId == null ? UUID.randomUUID().toString() : taskId;
        Task task = new Task(actualTaskId, name, group, timeout, metadata == null ? Map.of() : metadata);

        synchronized (lock) {
            if (registry.contains(actualTaskId)) {
                throw new DuplicateTaskError("Task " + actualTaskId + " already exists");
            }
            task.setParentTaskId(TaskContext.getCurrentTaskId());
            registry.add(task);
        }

        CompletableFuture<Object> future = task.execute(callable, this::triggerStatusEvent, catchExceptions,
                executorService);
        scheduleTimeout(task, future);
        trackInCurrentGroup(task, future);
        triggerEvent(TaskManagerEvents.TASK_CREATED, task);
        LOGGER.fine(() -> "Created task " + actualTaskId + " name=" + name + " parent=" + task.getParentTaskId());
        return task;
    }

    public TaskGroupScope taskGroup() {
        return new TaskGroupScope();
    }

    public void cascadeCancel(String taskId) {
        cascadeCancel(taskId, "parent_cancelled");
    }

    public void cascadeCancel(String taskId, String reason) {
        Task task = registry.get(taskId);
        if (task != null && !task.isTerminal()) {
            cancelTask(taskId, reason == null ? "parent_cancelled" : reason, task.getCancelledBy());
        }
        cascadeCancelChildren(taskId, reason == null ? "parent_cancelled" : reason);
    }

    public boolean cancelTask(String taskId, String reason) {
        return cancelTask(taskId, reason, null);
    }

    public boolean cancelTask(String taskId, String reason, String cancelledBy) {
        Task task = registry.get(taskId);
        if (task == null || task.isTerminal()) {
            return false;
        }
        if (cancelledBy != null) {
            task.setCancelledBy(cancelledBy);
        }
        task.markCancelled(reason == null ? "manual_cancel" : reason, cancelledBy);
        CompletableFuture<?> future = task.getExecutionFuture();
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
        triggerEvent(TaskManagerEvents.TASK_CANCELLED, task);
        return true;
    }

    public int cancelGroup(String group) {
        List<Task> tasks = registry.getByGroup(group);
        int count = 0;
        for (Task task : tasks) {
            if (cancelTask(task.getTaskId(), "manual_cancel")) {
                count++;
            }
        }
        return count;
    }

    public int cancelAll() {
        int count = 0;
        for (Task task : registry.getRunning()) {
            if (cancelTask(task.getTaskId(), "manual_cancel")) {
                count++;
            }
        }
        return count;
    }

    public String getTaskTree(String taskId) {
        List<String> lines = new ArrayList<>();
        buildTreeRecursive(taskId, lines, 0);
        return String.join(System.lineSeparator(), lines);
    }

    public CompletableFuture<List<Object>> waitGroup(String group) {
        return waitGroup(group, null, false);
    }

    public CompletableFuture<List<Object>> waitGroup(String group, Duration timeout, boolean returnExceptions) {
        List<String> taskIds;
        synchronized (lock) {
            taskIds = registry.getGroupTaskIds(group);
        }
        List<Task> tasks = new ArrayList<>();
        for (String taskId : taskIds) {
            Task task = registry.get(taskId);
            if (task != null) {
                tasks.add(task);
            }
        }
        return CompletableFuture.supplyAsync(() -> waitForTasks(tasks, timeout, returnExceptions), executorService);
    }

    public CompletableFuture<List<Object>> waitAll() {
        return waitAll(null, false);
    }

    public CompletableFuture<List<Object>> waitAll(Duration timeout, boolean returnExceptions) {
        List<Task> tasks;
        synchronized (lock) {
            tasks = registry.keys().stream().map(registry::get).filter(Objects::nonNull).toList();
        }
        return CompletableFuture.supplyAsync(() -> waitForTasks(tasks, timeout, returnExceptions), executorService);
    }

    public List<TaskResult> asCompleted(List<Task> tasks) {
        return asCompleted(tasks, null);
    }

    public List<TaskResult> asCompleted(List<Task> tasks, Duration timeout) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        LinkedBlockingQueue<TaskResult> queue = new LinkedBlockingQueue<>();
        for (Task task : tasks) {
            task.waitResult().whenComplete((value, throwable) -> {
                Throwable unwrapped = throwable == null ? null : Task.unwrap(throwable);
                queue.add(new TaskResult(task, unwrapped == null ? value : unwrapped));
            });
        }
        List<TaskResult> results = new ArrayList<>();
        long deadline = timeout == null ? 0L : System.nanoTime() + timeout.toNanos();
        while (results.size() < tasks.size()) {
            try {
                TaskResult result;
                if (timeout == null) {
                    result = queue.take();
                } else {
                    long remainingNanos = deadline - System.nanoTime();
                    if (remainingNanos <= 0L) {
                        throw new CompletionException(new TimeoutException("as_completed() timed out"));
                    }
                    result = queue.poll(remainingNanos, TimeUnit.NANOSECONDS);
                    if (result == null) {
                        throw new CompletionException(new TimeoutException("as_completed() timed out"));
                    }
                }
                results.add(result);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CompletionException(interrupted);
            }
        }
        return results;
    }

    public void on(String eventType, Consumer<Task> callback) {
        callbacks.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>()).add(callback);
    }

    public void onCreated(Consumer<Task> callback) {
        on(TaskManagerEvents.TASK_CREATED, callback);
    }

    public void onRunning(Consumer<Task> callback) {
        on(TaskManagerEvents.TASK_RUNNING, callback);
    }

    public void onCompleted(Consumer<Task> callback) {
        on(TaskManagerEvents.TASK_COMPLETED, callback);
    }

    public void onFailed(Consumer<Task> callback) {
        on(TaskManagerEvents.TASK_FAILED, callback);
    }

    public void onCancelled(Consumer<Task> callback) {
        on(TaskManagerEvents.TASK_CANCELLED, callback);
    }

    public void onTimeout(Consumer<Task> callback) {
        on(TaskManagerEvents.TASK_TIMEOUT, callback);
    }

    public void off(String eventType, Consumer<Task> callback) {
        List<Consumer<Task>> registered = callbacks.get(eventType);
        if (registered != null) {
            registered.remove(callback);
        }
    }

    public boolean removeTask(String taskId) {
        synchronized (lock) {
            if (!registry.contains(taskId)) {
                return false;
            }
            registry.removeUnsafe(taskId);
            return true;
        }
    }

    public int removeCompleted() {
        synchronized (lock) {
            List<String> completed = registry.items().stream()
                    .filter(entry -> entry.getValue().isTerminal())
                    .map(Map.Entry::getKey)
                    .toList();
            for (String taskId : completed) {
                registry.removeUnsafe(taskId);
            }
            return completed.size();
        }
    }

    public void printTaskTree() {
        for (Task task : registry.getAll()) {
            if (task.getParentTaskId() == null) {
                LOGGER.fine(getTaskTree(task.getTaskId()));
            }
        }
    }

    public void printTaskTree(String taskId) {
        LOGGER.fine(getTaskTree(taskId));
    }

    public void shutdown() {
        executorService.shutdownNow();
        timeoutScheduler.shutdownNow();
    }

    public TaskRegistry getRegistry() {
        return registry;
    }

    public Task getTask(String taskId) {
        return registry.get(taskId);
    }

    public List<Task> getAllTasks() {
        return registry.getAll();
    }

    public List<Task> getTasksByGroup(String group) {
        return registry.getByGroup(group);
    }

    public List<Task> getTasksByStatus(TaskStatus status) {
        return registry.getByStatus(status);
    }

    public Map<String, Integer> getStats() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("total", registry.getAll().size());
        for (TaskStatus status : TaskStatus.values()) {
            stats.put(status.getValue(), 0);
        }
        for (Task task : registry.getAll()) {
            stats.computeIfPresent(task.getStatus().getValue(), (ignored, count) -> count + 1);
        }
        return stats;
    }

    public static Task createTaskGlobal(Callable<?> callable,
                                        String taskId,
                                        String name,
                                        String group,
                                        Double timeout,
                                        Map<String, Object> metadata,
                                        boolean catchExceptions) {
        return getInstance().createTask(callable, taskId, name, group, timeout, metadata, catchExceptions);
    }

    public static int cancelGroupGlobal(String group) {
        return getInstance().cancelGroup(group);
    }

    public static int cancelAllGlobal() {
        return getInstance().cancelAll();
    }

    private void scheduleTimeout(Task task, CompletableFuture<?> future) {
        Double timeout = task.getTimeout();
        if (timeout == null || timeout <= 0) {
            return;
        }
        long timeoutMillis = Math.max(1L, (long) Math.ceil(timeout * 1000D));
        timeoutScheduler.schedule(() -> {
            if (future.isDone() || task.isTerminal()) {
                return;
            }
            task.markTimeout();
            future.cancel(true);
            triggerEvent(TaskManagerEvents.TASK_TIMEOUT, task);
        }, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    private void trackInCurrentGroup(Task task, CompletableFuture<?> future) {
        Object currentGroup = TaskContext.getTaskGroup();
        if (currentGroup instanceof TaskGroupScope scope) {
            scope.track(task, future);
        }
    }

    private void cascadeCancelChildren(String parentId, String reason) {
        for (Task child : registry.getByParent(parentId)) {
            if (!child.isTerminal()) {
                cancelTask(child.getTaskId(), reason == null ? "parent_cancelled" : reason, parentId);
            }
            cascadeCancelChildren(child.getTaskId(), reason);
        }
    }

    private String buildCancelChain(String taskId) {
        List<String> chain = new ArrayList<>();
        String currentId = taskId;
        while (currentId != null) {
            Task task = registry.get(currentId);
            if (task == null) {
                break;
            }
            chain.add(task.getDisplayName() + "(" + currentId.substring(0, Math.min(8, currentId.length())) + ")");
            currentId = task.getCancelledBy();
        }
        if (chain.isEmpty()) {
            return taskId.substring(0, Math.min(8, taskId.length()));
        }
        List<String> reversedChain = new ArrayList<>(chain);
        java.util.Collections.reverse(reversedChain);
        return String.join(" -> ", reversedChain);
    }

    private void buildTreeRecursive(String taskId, List<String> lines, int indent) {
        Task task = registry.get(taskId);
        if (task == null) {
            return;
        }
        String prefix = "  ".repeat(Math.max(0, indent)) + (indent == 0 ? "" : "+- ");
        StringBuilder statusInfo = new StringBuilder("[").append(task.getStatus().getValue()).append("]");
        if (task.getCancelledBy() != null) {
            Task cancelledByTask = registry.get(task.getCancelledBy());
            String cancelledByName = cancelledByTask == null
                    ? task.getCancelledBy().substring(0, Math.min(8, task.getCancelledBy().length()))
                    : cancelledByTask.getDisplayName();
            statusInfo.append(" (cancelled by: ").append(cancelledByName)
                    .append(", reason: ").append(task.getCancelReason()).append(")");
        } else if (task.getCancelReason() != null) {
            statusInfo.append(" (reason: ").append(task.getCancelReason()).append(")");
        }
        lines.add(prefix + task.getDisplayName() + " " + statusInfo);
        for (Task child : registry.getByParent(taskId)) {
            buildTreeRecursive(child.getTaskId(), lines, indent + 1);
        }
    }

    private List<Object> waitForTasks(List<Task> tasks, Duration timeout, boolean returnExceptions) {
        List<Object> results = new ArrayList<>();
        Exception firstException = null;
        for (int index = 0; index < tasks.size(); index++) {
            Task task = tasks.get(index);
            if (task == null) {
                results.add(null);
                continue;
            }
            if (firstException != null && !returnExceptions) {
                task.cancel(false, "other_task_failed", null);
                results.add(null);
                continue;
            }
            try {
                Object result = timeout == null
                        ? task.waitResult().get()
                        : task.waitResult().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                results.add(result);
            } catch (Exception exception) {
                Exception normalized = normalizeWaitException(exception);
                if (returnExceptions) {
                    results.add(normalized);
                } else {
                    firstException = normalized;
                    results.add(null);
                    cancelRemaining(tasks, index + 1);
                }
            }
        }
        if (firstException != null && !returnExceptions) {
            throw new CompletionException(firstException);
        }
        return results;
    }

    private void cancelRemaining(List<Task> tasks, int startIndex) {
        for (int index = startIndex; index < tasks.size(); index++) {
            Task task = tasks.get(index);
            if (task != null) {
                task.cancel(false, "other_task_failed", null);
            }
        }
    }

    private static Exception normalizeWaitException(Exception exception) {
        Throwable throwable = exception;
        if (exception instanceof java.util.concurrent.ExecutionException executionException
                && executionException.getCause() != null) {
            throwable = executionException.getCause();
        }
        if (throwable instanceof Exception checkedException) {
            return checkedException;
        }
        return new RuntimeException(throwable);
    }

    private void triggerEvent(String eventType, Task task) {
        List<Consumer<Task>> registered = callbacks.getOrDefault(eventType, new CopyOnWriteArrayList<>());
        for (Consumer<Task> callback : registered) {
            try {
                callback.accept(task);
            } catch (RuntimeException exception) {
                LOGGER.fine(() -> "Task callback failed for event " + eventType + ": " + exception.getMessage());
            }
        }
    }

    private void triggerStatusEvent(Task task, String status) {
        switch (status) {
            case "running" -> triggerEvent(TaskManagerEvents.TASK_RUNNING, task);
            case "completed" -> triggerEvent(TaskManagerEvents.TASK_COMPLETED, task);
            case "cancelled" -> triggerEvent(TaskManagerEvents.TASK_CANCELLED, task);
            case "timeout" -> triggerEvent(TaskManagerEvents.TASK_TIMEOUT, task);
            case "failed" -> triggerEvent(TaskManagerEvents.TASK_FAILED, task);
            default -> {
            }
        }
    }

    /**
     * Auto-closeable task-group scope for Java try-with-resources usage.
     *
     * <p>Mirrors Python's {@code TaskManager.task_group} in
     * {@code openjiuwen/core/common/task_manager/manager.py}.</p>
     */
    public final class TaskGroupScope implements AutoCloseable {
        private final TaskContext.ContextToken<Object> token;
        private final List<CompletableFuture<?>> trackedFutures = new CopyOnWriteArrayList<>();

        private TaskGroupScope() {
            this.token = TaskContext.setTaskGroup(this);
        }

        private void track(Task task, CompletableFuture<?> future) {
            trackedFutures.add(future);
        }

        @Override
        public void close() {
            try {
                CompletableFuture.allOf(trackedFutures.toArray(CompletableFuture[]::new)).join();
            } finally {
                TaskContext.resetTaskGroup(token);
            }
        }
    }

    /**
     * Pair yielded by {@link #asCompleted(List, Duration)}.
     *
     * <p>Mirrors Python's {@code TaskManager.as_completed} tuple in
     * {@code openjiuwen/core/common/task_manager/manager.py}.</p>
     */
    public record TaskResult(Task task, Object result) {
    }
}
