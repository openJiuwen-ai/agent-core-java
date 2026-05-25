/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import com.openjiuwen.core.common.task_manager.TaskExceptions.DuplicateTaskError;
import com.openjiuwen.core.common.task_manager.TaskExceptions.TaskNotFoundError;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
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
        
        // Execute task
        CompletableFuture<?> future = CompletableFuture.supplyAsync(() -> {
            try {
                triggerEvent("running", task);
                currentTaskId.set(actualTaskId);
                
                Object result = runnable.call();
                
                task.setStatus(TaskStatus.COMPLETED);
                task.setResult(result);
                triggerEvent("completed", task);
                
                return result;
            } catch (Exception e) {
                if (catchExceptions) {
                    task.setStatus(TaskStatus.FAILED);
                    task.setError(e.getMessage());
                    triggerEvent("failed", task);
                    return null;
                } else {
                    throw new CompletionException(e);
                }
            } finally {
                currentTaskId.remove();
            }
        }, executorService);
        
        // Handle timeout
        if (timeout != null && timeout > 0) {
            ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(() -> {
                if (!future.isDone()) {
                    future.cancel(true);
                    task.setStatus(TaskStatus.TIMEOUT);
                    triggerEvent("timeout", task);
                    logger.warning("Task " + actualTaskId + " timed out after " + timeout + "s");
                }
            }, timeout.longValue(), TimeUnit.SECONDS);
            
            // Clean up timeout scheduler when task completes
            future.whenComplete((r, e) -> timeoutFuture.cancel(false));
        }
        
        task.setFuture(future);
        triggerEvent("created", task);
        
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
        cancelTask(taskId, reason);
        
        // Cascade cancel children
        List<Task> children = registry.getByParent(taskId);
        for (Task child : children) {
            if (!child.isTerminal()) {
                child.setCancelledBy(taskId);
                cancelTask(child.getTaskId(), reason);
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
    public void cancelTask(String taskId, String reason) {
        Task task = registry.get(taskId);
        if (task == null || task.isTerminal()) {
            return;
        }
        
        CompletableFuture<?> future = task.getFuture();
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
        
        task.setStatus(TaskStatus.CANCELLED);
        task.setCancelledBy(taskId);
        triggerEvent("cancelled", task);
        
        logger.fine("Cancelled task: " + taskId + " reason=" + reason);
    }

    /**
     * Cancel all tasks in a group.
     *
     * @param group Group name to cancel
     * @param reason Reason for cancellation
     */
    public void cancelGroup(String group, String reason) {
        List<Task> tasks = registry.getByGroup(group);
        for (Task task : tasks) {
            if (!task.isTerminal()) {
                cancelTask(task.getTaskId(), reason);
            }
        }
    }

    /**
     * Cancel all running tasks.
     */
    public void cancelAll() {
        List<Task> tasks = registry.getAll();
        for (Task task : tasks) {
            if (!task.isTerminal()) {
                cancelTask(task.getTaskId(), "cancel_all");
            }
        }
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
        // Placeholder for callback framework events
        logger.fine("Task event: " + eventType + " task=" + task.getTaskId());
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