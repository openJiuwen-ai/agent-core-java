/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import com.openjiuwen.core.common.task_manager.TaskExceptions.DuplicateTaskError;
import com.openjiuwen.core.common.task_manager.TaskExceptions.TaskNotFoundError;
import com.openjiuwen.core.runner.callback.TaskManagerEvents;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.UUID;

/**
 * Coroutine Task Manager.
 * <p>
 * Unified manager for all coroutine tasks with structured concurrency.
 * Uses WeakHashMap for automatic cleanup when tasks are no longer referenced.
 * <p>
 * Mirrors Python's {@code TaskManager} in
 * {@code openjiuwen.core.common.task_manager.manager}.
 * <p>
 * Note: In Java, this uses CompletableFuture and ExecutorService for 
 * structured concurrency, replacing Python's asyncio.TaskGroup.
 */
public class TaskManager {

    private static final Logger logger = Logger.getLogger(TaskManager.class.getName());
    
    // Singleton instance
    private static volatile TaskManager instance = null;
    private static final Object INSTANCE_LOCK = new Object();
    
    private TaskRegistry registry;
    private final ReentrantLock lock;
    private final ExecutorService executorService;
    private final ScheduledExecutorService timeoutScheduler;
    private boolean initialized = false;
    private final Object callbackFramework;
    private final Map<String, CopyOnWriteArrayList<Consumer<Task>>> callbacks = new ConcurrentHashMap<>();
    
    // Task group context (ThreadLocal for structured concurrency)
    private static final ThreadLocal<TaskGroupContext> currentTaskGroup = new ThreadLocal<>();
    private static final ThreadLocal<String> currentTaskId = new ThreadLocal<>();

    /**
     * Get singleton instance.
     */
    public static TaskManager getInstance() {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new TaskManager();
                }
            }
        }
        return instance;
    }

    /**
     * Reset singleton instance (for testing purposes).
     */
    public static void resetInstance() {
        synchronized (INSTANCE_LOCK) {
            if (instance != null) {
                instance.shutdown();
                instance = null;
            }
        }
    }

    private TaskManager() {
        this.registry = new TaskRegistry();
        this.lock = new ReentrantLock();
        this.executorService = Executors.newCachedThreadPool();
        this.timeoutScheduler = Executors.newScheduledThreadPool(1);
        this.initialized = true;
        this.callbackFramework = null;
        
        logger.info("CoroutineTaskManager initialized with WeakHashMap auto-cleanup");
    }

    /**
     * Create and register a new task.
     *
     * @param runnable The task to execute
     * @param taskId Optional custom task ID (auto-generated if null)
     * @param name Optional friendly name
     * @param group Optional group name
     * @param timeout Optional timeout in seconds
     * @param metadata Optional metadata dict
     * @param catchExceptions Whether to catch exceptions
     * @return The created Task object
     */
    public Task createTask(
        Callable<?> runnable,
        String taskId,
        String name,
        String group,
        Double timeout,
        Map<String, Object> metadata,
        boolean catchExceptions
    ) {
        String actualTaskId = taskId != null ? taskId : UUID.randomUUID().toString();
        
        Task task = new Task(
            actualTaskId,
            name,
            group,
            timeout,
            metadata != null ? metadata : new HashMap<>()
        );
        
        lock.lock();
        try {
            if (registry.contains(actualTaskId)) {
                throw new DuplicateTaskError("Task " + actualTaskId + " already exists");
            }
            
            task.setParentTaskId(currentTaskId.get());
            registry.add(task);
        } finally {
            lock.unlock();
        }
        
        // Execute task. Java uses ExecutorService instead of Python's anyio TaskGroup.
        CompletableFuture<?> future = CompletableFuture.supplyAsync(() -> {
            try {
                task.setStatus(TaskStatus.RUNNING);
                task.setStartedAt(java.time.Instant.now());
                triggerEvent(TaskManagerEvents.TASK_RUNNING, task);
                currentTaskId.set(actualTaskId);
                
                Object result = runnable.call();

                if (!task.isTerminal()) {
                    task.complete(result);
                    triggerEvent(TaskManagerEvents.TASK_COMPLETED, task);
                }
                
                return result;
            } catch (CancellationException e) {
                if (!task.isTerminal()) {
                    task.cancel(null, "manual_cancel");
                    triggerEvent(TaskManagerEvents.TASK_CANCELLED, task);
                }
                return null;
            } catch (Exception e) {
                task.fail(e);
                triggerEvent(TaskManagerEvents.TASK_FAILED, task);
                if (catchExceptions) {
                    return null;
                }
                throw new CompletionException(e);
            } finally {
                currentTaskId.remove();
            }
        }, executorService);

        TaskGroupContext taskGroup = currentTaskGroup.get();
        if (taskGroup != null && taskGroup.isActive()) {
            taskGroup.track(task, future);
        }
        
        // Handle timeout
        if (timeout != null && timeout > 0) {
            ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(() -> {
                if (!future.isDone()) {
                    future.cancel(true);
                    task.setStatus(TaskStatus.TIMEOUT);
                    task.setException(new TimeoutException("Task timeout"));
                    task.setFinishedAt(java.time.Instant.now());
                    task.getDoneFuture().completeExceptionally(task.getException());
                    triggerEvent(TaskManagerEvents.TASK_TIMEOUT, task);
                    logger.warning("Task " + actualTaskId + " timed out after " + timeout + "s");
                }
            }, Math.max(1L, Math.round(timeout * 1000)), TimeUnit.MILLISECONDS);
            
            // Clean up timeout scheduler when task completes
            future.whenComplete((r, e) -> timeoutFuture.cancel(false));
        }
        
        task.setFuture(future);
        triggerEvent(TaskManagerEvents.TASK_CREATED, task);
        
        logger.fine("Created task: " + actualTaskId + " name=" + name + 
            " parent=" + task.getParentTaskId());
        
        return task;
    }

    /**
     * Create async task group context.
     */
    public TaskGroupContext createTaskGroup() {
        TaskGroupContext tg = new TaskGroupContext(executorService);
        currentTaskGroup.set(tg);
        return tg;
    }

    /**
     * Cancel a task and all its children recursively.
     *
     * @param taskId Task ID to cancel
     * @param reason Reason for cancellation
     */
    public void cascadeCancel(String taskId, String reason) {
        Task task = registry.get(taskId);
        if (task == null) {
            return;
        }
        
        // Cancel the task
        if (!task.isTerminal()) {
            cancelTask(taskId, reason != null ? reason : "manual_cancel", null);
        }
        
        // Cascade cancel children
        List<Task> children = registry.getByParent(taskId);
        for (Task child : children) {
            if (!child.isTerminal()) {
                cancelTask(child.getTaskId(), reason != null ? reason : "parent_cancelled", taskId);
                cascadeCancel(child.getTaskId(), reason);
            }
        }
    }

    /**
     * Cancel a single task.
     *
     * @param taskId Task ID to cancel
     * @param reason Reason for cancellation
     */
    public boolean cancelTask(String taskId, String reason) {
        return cancelTask(taskId, reason, null);
    }

    public boolean cancelTask(String taskId, String reason, String cancelledBy) {
        Task task = registry.get(taskId);
        if (task == null || task.isTerminal()) {
            return false;
        }
        
        CompletableFuture<?> future = task.getFuture();
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
        
        task.cancel(cancelledBy, reason != null ? reason : "manual_cancel");
        triggerEvent(TaskManagerEvents.TASK_CANCELLED, task);
        
        logger.fine("Cancelled task: " + taskId + " reason=" + reason);
        return true;
    }

    /**
     * Cancel all tasks in a group.
     *
     * @param group Group name to cancel
     * @param reason Reason for cancellation
     */
    public int cancelGroup(String group, String reason) {
        List<Task> tasks = registry.getByGroup(group);
        int count = 0;
        for (Task task : tasks) {
            if (cancelTask(task.getTaskId(), reason != null ? reason : "manual_cancel")) {
                count++;
            }
        }
        return count;
    }

    /**
     * Cancel all running tasks.
     */
    public int cancelAll() {
        List<Task> tasks = registry.getAll();
        int count = 0;
        for (Task task : tasks) {
            if (task.getStatus() == TaskStatus.RUNNING
                    && cancelTask(task.getTaskId(), "manual_cancel")) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get a task by ID.
     *
     * @param taskId Task ID
     * @return Task object or null
     */
    public Task getTask(String taskId) {
        return registry.get(taskId);
    }

    /**
     * Get all tasks.
     *
     * @return List of all tasks
     */
    public List<Task> getAllTasks() {
        return registry.getAll();
    }

    /**
     * Get running tasks.
     *
     * @return List of running tasks
     */
    public List<Task> getRunningTasks() {
        return registry.getByStatus(TaskStatus.RUNNING);
    }

    public List<Task> getTasksByGroup(String group) {
        return registry.getByGroup(group);
    }

    public List<Task> getTasksByStatus(TaskStatus status) {
        return registry.getByStatus(status);
    }

    public int cancelGroup(String group) {
        return cancelGroup(group, "manual_cancel");
    }

    /**
     * Wait for all tasks to complete.
     *
     * @return CompletableFuture that completes when all tasks are done
     */
    public CompletableFuture<Void> waitAll() {
        List<Task> tasks = registry.getAll();
        CompletableFuture<?>[] futures = tasks.stream()
            .map(Task::getFuture)
            .filter(f -> f != null && !f.isDone())
            .toArray(CompletableFuture[]::new);
        
        return CompletableFuture.allOf(futures);
    }

    public CompletableFuture<List<Object>> waitAllResults() {
        return waitAllResults(false);
    }

    public CompletableFuture<List<Object>> waitAllResults(boolean returnExceptions) {
        return CompletableFuture.supplyAsync(() -> waitForTasks(registry.getAll(), returnExceptions), executorService);
    }

    /**
     * Wait for task group to complete.
     *
     * @param group Group name
     * @return CompletableFuture that completes when group tasks are done
     */
    public CompletableFuture<Void> waitGroup(String group) {
        List<Task> tasks = registry.getByGroup(group);
        CompletableFuture<?>[] futures = tasks.stream()
            .map(Task::getFuture)
            .filter(f -> f != null && !f.isDone())
            .toArray(CompletableFuture[]::new);
        
        return CompletableFuture.allOf(futures);
    }

    public CompletableFuture<List<Object>> waitGroupResults(String group) {
        return waitGroupResults(group, false);
    }

    public CompletableFuture<List<Object>> waitGroupResults(String group, boolean returnExceptions) {
        return CompletableFuture.supplyAsync(() -> waitForTasks(registry.getByGroup(group), returnExceptions),
                executorService);
    }

    private List<Object> waitForTasks(List<Task> tasks, boolean returnExceptions) {
        List<Object> results = new ArrayList<>();
        Exception firstException = null;
        for (Task task : tasks) {
            try {
                results.add(task.waitForResult());
            } catch (Exception e) {
                if (returnExceptions) {
                    results.add(e);
                } else {
                    firstException = e;
                    break;
                }
            }
        }
        if (firstException != null && !returnExceptions) {
            throw new CompletionException(firstException);
        }
        return results;
    }

    public boolean removeTask(String taskId) {
        lock.lock();
        try {
            return registry.remove(taskId) != null;
        } finally {
            lock.unlock();
        }
    }

    public int removeCompleted() {
        lock.lock();
        try {
            List<String> terminalIds = registry.getAll().stream()
                    .filter(Task::isTerminal)
                    .map(Task::getTaskId)
                    .toList();
            terminalIds.forEach(registry::remove);
            return terminalIds.size();
        } finally {
            lock.unlock();
        }
    }

    public Map<String, Integer> getStats() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        List<Task> tasks = registry.getAll();
        stats.put("total", tasks.size());
        for (TaskStatus status : TaskStatus.values()) {
            stats.put(status.getValue(), 0);
        }
        for (Task task : tasks) {
            stats.computeIfPresent(task.getStatus().getValue(), (ignored, value) -> value + 1);
        }
        return stats;
    }

    public String getTaskTree(String taskId) {
        List<String> lines = new ArrayList<>();
        buildTreeRecursive(taskId, lines, 0);
        return String.join(System.lineSeparator(), lines);
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
            String displayName = cancelledByTask != null
                    ? cancelledByTask.getDisplayName()
                    : task.getCancelledBy().substring(0, Math.min(8, task.getCancelledBy().length()));
            statusInfo.append(" (cancelled by: ").append(displayName)
                    .append(", reason: ").append(task.getCancelReason()).append(")");
        } else if (task.getCancelReason() != null) {
            statusInfo.append(" (reason: ").append(task.getCancelReason()).append(")");
        }
        lines.add(prefix + task.getDisplayName() + " " + statusInfo);
        for (Task child : registry.getByParent(taskId)) {
            buildTreeRecursive(child.getTaskId(), lines, indent + 1);
        }
    }

    /**
     * Shutdown the task manager.
     */
    public void shutdown() {
        executorService.shutdown();
        timeoutScheduler.shutdown();
        
        try {
            executorService.awaitTermination(10, TimeUnit.SECONDS);
            timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            timeoutScheduler.shutdownNow();
        }
        
        logger.info("TaskManager shutdown complete");
    }

    // -- Event triggers --

    private void triggerEvent(String eventType, Task task) {
        List<Consumer<Task>> registered = callbacks.getOrDefault(eventType, new CopyOnWriteArrayList<>());
        for (Consumer<Task> callback : registered) {
            try {
                callback.accept(task);
            } catch (RuntimeException e) {
                logger.fine("Task callback failed for event " + eventType + ": " + e.getMessage());
            }
        }
        logger.fine("Task event: " + eventType + " task=" + task.getTaskId());
    }

    public void on(String eventType, Consumer<Task> callback) {
        callbacks.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>()).add(callback);
    }

    public void off(String eventType, Consumer<Task> callback) {
        List<Consumer<Task>> registered = callbacks.get(eventType);
        if (registered != null) {
            registered.remove(callback);
        }
    }

    public void printTaskTree() {
        List<Task> roots = registry.getAll().stream()
                .filter(task -> task.getParentTaskId() == null)
                .toList();
        for (Task root : roots) {
            logger.fine(getTaskTree(root.getTaskId()));
        }
    }

    public void printTaskTree(String taskId) {
        logger.fine(getTaskTree(taskId));
    }

    // -- Context accessors --

    public static TaskGroupContext getTaskGroup() {
        return currentTaskGroup.get();
    }

    public static String getCurrentTaskId() {
        return currentTaskId.get();
    }

    public static void setTaskGroup(TaskGroupContext tg) {
        currentTaskGroup.set(tg);
    }

    public static void resetTaskGroup() {
        currentTaskGroup.remove();
    }

    // -- Getters --

    public TaskRegistry getRegistry() { return registry; }
}
