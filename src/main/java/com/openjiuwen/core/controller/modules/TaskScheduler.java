/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskCompletionEvent;
import com.openjiuwen.core.controller.schema.TaskFailedEvent;
import com.openjiuwen.core.controller.schema.TaskInteractionEvent;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.session.AgentSessionApi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Task scheduler responsible for scheduling, executing, pausing, and canceling tasks.
 * <p>
 * Supports concurrent execution of multiple tasks and streaming task execution output.
 * <p>
 * Workflow:
 * <ol>
 *   <li>Periodically scan for tasks to be executed (status = SUBMITTED)</li>
 *   <li>Execute multiple tasks concurrently using virtual threads</li>
 *   <li>Stream output generated during task execution</li>
 *   <li>Update task status based on output type</li>
 * </ol>
 * <p>
 * Mirrors Python's {@code TaskScheduler}.
 */
public class TaskScheduler {

    private ControllerConfig config;
    private final TaskManager taskManager;
    private final ContextEngine contextEngine;
    private final Object abilityManager;
    private final EventQueue eventQueue;
    private final TaskExecutorRegistry taskExecutorRegistry = new TaskExecutorRegistry();
    private final Map<String, AgentSessionApi> sessions = new ConcurrentHashMap<>();
    private final BaseCard card;

    private volatile boolean running = false;
    private ScheduledFuture<?> schedulerFuture;
    private ScheduledExecutorService scheduler;

    /**
     * Running tasks: taskId -> RunningTaskEntry (executor + future).
     */
    private final Map<String, RunningTaskEntry> runningTasks = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public TaskScheduler(
            ControllerConfig config,
            TaskManager taskManager,
            ContextEngine contextEngine,
            Object abilityManager,
            EventQueue eventQueue,
            BaseCard card
    ) {
        this.config = config;
        this.taskManager = taskManager;
        this.contextEngine = contextEngine;
        this.abilityManager = abilityManager;
        this.eventQueue = eventQueue;
        this.card = card;
    }

    public ControllerConfig getConfig() {
        return config;
    }

    public void setConfig(ControllerConfig config) {
        this.config = config;
    }

    public Map<String, AgentSessionApi> getSessions() {
        return sessions;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public TaskExecutorRegistry getTaskExecutorRegistry() {
        return taskExecutorRegistry;
    }

    // ==================== Task Execution ====================

    private void handleTaskExecutionFailure(String taskId, AgentSessionApi session, String errorMessage) {
        taskManager.updateTaskStatus(taskId, TaskStatus.FAILED, errorMessage);

        ControllerOutputChunk failedChunk = new ControllerOutputChunk(
                0,
                new ControllerOutputPayload(
                        EventType.TASK_FAILED.getValue(),
                        List.of(new DataFrame.TextDataFrame(errorMessage)),
                        null
                ),
                false
        );
        publishTaskEvent(taskId, session, failedChunk);
    }

    @SuppressWarnings("resource")
    private void executeTaskWrapper(String taskId, AgentSessionApi session) {
        try {
            if (config.getTaskTimeout() != null) {
                // Execute with timeout: run on current virtual thread, use a watchdog to interrupt
                Thread currentThread = Thread.currentThread();
                ScheduledExecutorService watchdog = new ScheduledThreadPoolExecutor(1, r -> {
                    Thread t = new Thread(r, "task-timeout-" + taskId);
                    t.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                        @Override
                        public void uncaughtException(Thread t, Throwable e) {
                            Loggers.CONTROLLER.error("executeTaskWrapper Error,Thread {} , {}", t.getName(),e.getMessage());
                        }
                    });
                    t.setDaemon(true);
                    return t;
                });
                long timeoutMs = (long) (config.getTaskTimeout() * 1000);
                ScheduledFuture<?> timeout = watchdog.schedule(() -> {
                    Loggers.CONTROLLER.error("Task {} timed out after {} seconds", taskId, config.getTaskTimeout());
                    currentThread.interrupt();
                }, timeoutMs, TimeUnit.MILLISECONDS);
                try {
                    executeTask(taskId, session);
                } finally {
                    timeout.cancel(false);
                    watchdog.shutdown();
                }
            } else {
                executeTask(taskId, session);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                String errorMsg = "Task timeout after " + config.getTaskTimeout() + " seconds";
                Loggers.CONTROLLER.error("Task {} {}", taskId, errorMsg);
                handleTaskExecutionFailure(taskId, session, errorMsg);
                Thread.currentThread().interrupt();
            } else {
                Loggers.CONTROLLER.error("Task {} execution failed: {}", taskId, e.getMessage());
                handleTaskExecutionFailure(taskId, session, e.getMessage());
            }
        } finally {
            lock.lock();
            try {
                runningTasks.remove(taskId);
            } finally {
                lock.unlock();
            }
            ensureSessionCompletionSignal(session.getSessionId());
        }
    }

    private void executeTask(String taskId, AgentSessionApi session) {
        // 1. Get task object
        List<Task> tasks = taskManager.getTask(TaskFilter.byTaskId(taskId));
        if (tasks.isEmpty()) {
            Loggers.CONTROLLER.error("Task {} not found", taskId);
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_TASK_EXECUTION_ERROR,
                    "error_msg", "task " + taskId + " not found");
        }
        Task task = tasks.get(0);
        Loggers.CONTROLLER.info("Executing task {} (type: {})", taskId, task.getTaskType());

        // 2. Create TaskExecutor
        TaskExecutorDependencies dependencies = new TaskExecutorDependencies(
                config, abilityManager, contextEngine, taskManager, eventQueue
        );
        TaskExecutor executor = taskExecutorRegistry.getTaskExecutor(task.getTaskType(), dependencies);

        // Update running task entry with executor
        lock.lock();
        try {
            RunningTaskEntry entry = runningTasks.get(taskId);
            if (entry != null) {
                entry.setExecutor(executor);
            }
        } finally {
            lock.unlock();
        }

        // 3. Update task status to WORKING
        taskManager.updateTaskStatus(taskId, TaskStatus.WORKING);

        // 4. Execute task and stream output
        Iterator<ControllerOutputChunk> chunks = executor.executeAbility(taskId, session);
        while (chunks.hasNext()) {
            ControllerOutputChunk chunk = chunks.next();

            // Write to session stream
            session.writeStream(chunk);

            // Check output type
            if (chunk.getControllerPayload() != null && chunk.getControllerPayload().getType() != null) {
                String payloadType = chunk.getControllerPayload().getType();

                if (EventType.TASK_COMPLETION.getValue().equals(payloadType)) {
                    Loggers.CONTROLLER.info("Task {} completed", taskId);
                    taskManager.updateTaskStatus(taskId, TaskStatus.COMPLETED);
                    publishTaskEvent(taskId, session, chunk);
                    break;
                } else if (EventType.TASK_INTERACTION.getValue().equals(payloadType)) {
                    Loggers.CONTROLLER.info("Task {} requires interaction", taskId);
                    taskManager.updateTaskStatus(taskId, TaskStatus.INPUT_REQUIRED);
                    publishTaskEvent(taskId, session, chunk);
                    break;
                } else if (EventType.TASK_FAILED.getValue().equals(payloadType)) {
                    Loggers.CONTROLLER.error("Task {} failed", taskId);
                    taskManager.updateTaskStatus(taskId, TaskStatus.FAILED);
                    publishTaskEvent(taskId, session, chunk);
                    break;
                }
                // "processing" -> continue
            }
        }
    }

    // ==================== Completion Signal ====================

    private boolean areAllTasksCompleted(String sessionId) {
        try {
            List<Task> sessionTasks = taskManager.getTask(
                    TaskFilter.bySessionId(sessionId)
            );
            if (sessionTasks.isEmpty()) {
                Loggers.CONTROLLER.warning("No tasks found for session {}", sessionId);
                return true;
            }
            for (Task t : sessionTasks) {
                if (t.getStatus() == TaskStatus.SUBMITTED || t.getStatus() == TaskStatus.WORKING) {
                    return false;
                }
            }
            Loggers.CONTROLLER.info("No active tasks for session {}", sessionId);
            return true;
        } catch (Exception e) {
            Loggers.CONTROLLER.error("Error checking task completion status: {}", e.getMessage());
            return false;
        }
    }

    private void ensureSessionCompletionSignal(String sessionId) {
        try {
            if (!areAllTasksCompleted(sessionId)) {
                Loggers.CONTROLLER.info("Not all tasks completed, continue");
                return;
            }
            Loggers.CONTROLLER.info("All tasks completed for session {}, sending completion signal", sessionId);

            AgentSessionApi session = sessions.get(sessionId);
            if (session == null) {
                Loggers.CONTROLLER.warning("Session {} not found, cannot send completion signal", sessionId);
                return;
            }

            ControllerOutputChunk completionChunk = new ControllerOutputChunk(
                    0,
                    ControllerOutputPayload.allTasksProcessed(
                            "All tasks have been successfully processed"
                    ),
                    true
            );
            session.writeStream(completionChunk);
            Loggers.CONTROLLER.info("Completion signal sent for session {}", sessionId);
        } catch (Exception e) {
            Loggers.CONTROLLER.error("Unexpected error in ensureSessionCompletionSignal: {}", e.getMessage());
        }
    }

    // ==================== Event Publishing ====================

    private void publishTaskEvent(String taskId, AgentSessionApi session, ControllerOutputChunk chunk) {
        if (chunk.getControllerPayload() == null || chunk.getControllerPayload().getType() == null) {
            Loggers.CONTROLLER.error("Invalid chunk for task {}: missing payload or type", taskId);
            return;
        }

        List<Task> tasks = taskManager.getTask(TaskFilter.byTaskId(taskId));
        if (tasks.isEmpty()) {
            Loggers.CONTROLLER.error("Task {} not found in TaskManager", taskId);
            return;
        }
        Task task = tasks.get(0);
        String payloadType = chunk.getControllerPayload().getType();
        List<DataFrame> payloadData = chunk.getControllerPayload().getData() != null
                ? chunk.getControllerPayload().getData() : List.of();

        com.openjiuwen.core.controller.schema.Event event;
        if (EventType.TASK_COMPLETION.getValue().equals(payloadType)) {
            event = new TaskCompletionEvent(payloadData, task);
        } else if (EventType.TASK_INTERACTION.getValue().equals(payloadType)) {
            event = new TaskInteractionEvent(payloadData, task);
        } else if (EventType.TASK_FAILED.getValue().equals(payloadType)) {
            String errorMsg = "Unknown error";
            if (!payloadData.isEmpty() && payloadData.get(0) instanceof DataFrame.TextDataFrame tdf) {
                errorMsg = tdf.text();
            }
            task.setErrorMessage(errorMsg);
            event = new TaskFailedEvent(errorMsg, task);
        } else {
            Loggers.CONTROLLER.error("Unsupported payload type: {}", payloadType);
            return;
        }

        eventQueue.publishEvent(card.getId(), session, event);
        Loggers.CONTROLLER.info("Published {} for task {}", payloadType, taskId);
    }

    // ==================== Pause / Cancel ====================

    /**
     * Pause a running task.
     *
     * @param taskId task ID
     * @return whether pause succeeded
     */
    public boolean pauseTask(String taskId) {
        List<Task> tasks = taskManager.getTask(TaskFilter.byTaskId(taskId));
        if (tasks.isEmpty()) {
            Loggers.CONTROLLER.error("Task {} not found in TaskManager", taskId);
            return false;
        }
        Task task = tasks.get(0);

        AgentSessionApi session = sessions.get(task.getSessionId());
        if (session == null) {
            Loggers.CONTROLLER.error("Session {} not found for task {}", task.getSessionId(), taskId);
            return false;
        }

        RunningTaskEntry entry;
        lock.lock();
        try {
            entry = runningTasks.get(taskId);
            if (entry == null) {
                Loggers.CONTROLLER.warning("Task {} is not running, cannot pause", taskId);
                return false;
            }
        } finally {
            lock.unlock();
        }

        TaskExecutor executor = entry.getExecutor();
        if (executor != null) {
            try {
                TaskExecutor.PauseCheckResult checkResult = executor.canPause(taskId, session);
                if (!checkResult.canPause()) {
                    Loggers.CONTROLLER.warning("Task {} cannot be paused, reason: {}", taskId, checkResult.reason());
                    return false;
                }
                executor.pause(taskId, session);
            } catch (Exception e) {
                Loggers.CONTROLLER.error("Error pausing task {}: {}", taskId, e.getMessage());
                return false;
            }
        }

        // Cancel the thread
        lock.lock();
        try {
            entry = runningTasks.get(taskId);
            if (entry == null) {
                Loggers.CONTROLLER.warning("Task {} was already removed during pause operation", taskId);
                return false;
            }
            Thread taskThread = entry.getTaskThread();
            if (taskThread != null && taskThread.isAlive()) {
                taskThread.interrupt();
            }
        } finally {
            lock.unlock();
        }

        taskManager.updateTaskStatus(taskId, TaskStatus.PAUSED);
        Loggers.CONTROLLER.info("Task {} paused successfully", taskId);
        return true;
    }

    /**
     * Cancel a running task.
     *
     * @param taskId task ID
     * @return whether cancel succeeded
     */
    public boolean cancelTask(String taskId) {
        List<Task> tasks = taskManager.getTask(TaskFilter.byTaskId(taskId));
        if (tasks.isEmpty()) {
            Loggers.CONTROLLER.error("Task {} not found in TaskManager", taskId);
            return false;
        }
        Task task = tasks.get(0);

        AgentSessionApi session = sessions.get(task.getSessionId());
        if (session == null) {
            Loggers.CONTROLLER.error("Session {} not found for task {}", task.getSessionId(), taskId);
            return false;
        }

        RunningTaskEntry entry;
        lock.lock();
        try {
            entry = runningTasks.get(taskId);
            if (entry == null) {
                Loggers.CONTROLLER.warning("Task {} is not running, cannot cancel", taskId);
                return false;
            }
        } finally {
            lock.unlock();
        }

        TaskExecutor executor = entry.getExecutor();
        if (executor != null) {
            try {
                TaskExecutor.CancelCheckResult checkResult = executor.canCancel(taskId, session);
                if (!checkResult.canCancel()) {
                    Loggers.CONTROLLER.warning("Task {} cannot be cancelled: {}", taskId, checkResult.reason());
                    return false;
                }
                executor.cancel(taskId, session);
            } catch (Exception e) {
                Loggers.CONTROLLER.error("Error cancelling task {}: {}", taskId, e.getMessage());
                return false;
            }
        }

        lock.lock();
        try {
            entry = runningTasks.get(taskId);
            if (entry == null) {
                Loggers.CONTROLLER.warning("Task {} was already removed during cancel operation", taskId);
                return false;
            }
            Thread taskThread = entry.getTaskThread();
            if (taskThread != null && taskThread.isAlive()) {
                taskThread.interrupt();
            }
        } finally {
            lock.unlock();
        }

        taskManager.updateTaskStatus(taskId, TaskStatus.CANCELED);
        Loggers.CONTROLLER.info("Task {} cancelled successfully", taskId);
        return true;
    }

    // ==================== Schedule Loop ====================

    private void scheduleLoop() {
        Loggers.CONTROLLER.info("TaskScheduler schedule loop iteration");
        if (!running) {
            return;
        }
        try {
            List<Task> submittedTasks = taskManager.getTask(
                    TaskFilter.builder().status(TaskStatus.SUBMITTED).build()
            );

            for (Task task : submittedTasks) {
                AgentSessionApi session = sessions.get(task.getSessionId());
                if (session == null) {
                    Loggers.CONTROLLER.warning("Task {} session {} not found, skipping",
                            task.getTaskId(), task.getSessionId());
                    continue;
                }

                lock.lock();
                try {
                    if (runningTasks.size() >= config.getMaxConcurrentTasks()) {
                        Loggers.CONTROLLER.warning(
                                "Reached max concurrent tasks limit ({}), waiting for next schedule",
                                config.getMaxConcurrentTasks());
                        break;
                    }
                    if (runningTasks.containsKey(task.getTaskId())) {
                        continue;
                    }

                    // Start task on virtual thread
                    String taskId = task.getTaskId();
                    Thread virtualThread = Thread.ofVirtual()
                            .name("task-" + taskId)
                            .start(() -> executeTaskWrapper(taskId, session));

                    runningTasks.put(taskId, new RunningTaskEntry(null, virtualThread));
                } finally {
                    lock.unlock();
                }

                Loggers.CONTROLLER.info("Task {} ({}) started", task.getTaskId(), task.getTaskType());
            }
        } catch (Exception e) {
            Loggers.CONTROLLER.error("Error in schedule loop: {}", e.getMessage());
        }
    }

    // ==================== Start / Stop ====================

    /**
     * Start task scheduler.
     */
    public void start() {
        if (running) {
            Loggers.CONTROLLER.warning("TaskScheduler is already running");
            return;
        }
        running = true;
        scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "task-scheduler");
            t.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    Loggers.CONTROLLER.error("start Error,Thread {} , {}", t.getName(),e.getMessage());
                }
            });
            t.setDaemon(true);
            return t;
        });
        long intervalMs = (long) (config.getScheduleInterval() * 1000);
        schedulerFuture = scheduler.scheduleWithFixedDelay(
                this::scheduleLoop,
                0,
                intervalMs,
                TimeUnit.MILLISECONDS
        );
        Loggers.CONTROLLER.info("TaskScheduler started");
    }

    /**
     * Stop task scheduler.
     */
    public void stop() {
        if (!running) {
            Loggers.CONTROLLER.warning("TaskScheduler is not running");
            return;
        }
        running = false;

        // Cancel all running tasks
        lock.lock();
        try {
            for (Map.Entry<String, RunningTaskEntry> e : runningTasks.entrySet()) {
                Thread t = e.getValue().getTaskThread();
                if (t != null && t.isAlive()) {
                    t.interrupt();
                    Loggers.CONTROLLER.info("Cancelled task {}", e.getKey());
                }
            }
        } finally {
            lock.unlock();
        }

        if (schedulerFuture != null) {
            schedulerFuture.cancel(true);
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException ex) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Wait for running tasks
        List<Thread> threads;
        lock.lock();
        try {
            threads = new ArrayList<>();
            for (RunningTaskEntry entry : runningTasks.values()) {
                if (entry.getTaskThread() != null) {
                    threads.add(entry.getTaskThread());
                }
            }
        } finally {
            lock.unlock();
        }

        for (Thread t : threads) {
            try {
                t.join(5000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        lock.lock();
        try {
            runningTasks.clear();
        } finally {
            lock.unlock();
        }

        Loggers.CONTROLLER.info("TaskScheduler stopped");
    }

    // ==================== Inner class ====================

    /**
     * Tracks a running task's executor and thread.
     */
    private static class RunningTaskEntry {
        private volatile TaskExecutor executor;
        private final Thread taskThread;

        RunningTaskEntry(TaskExecutor executor, Thread taskThread) {
            this.executor = executor;
            this.taskThread = taskThread;
        }

        TaskExecutor getExecutor() {
            return executor;
        }

        void setExecutor(TaskExecutor executor) {
            this.executor = executor;
        }

        Thread getTaskThread() {
            return taskThread;
        }
    }
}
