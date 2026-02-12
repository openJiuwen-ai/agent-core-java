// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.contextengine.ContextEngine;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.*;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Task scheduler.
 *
 * <p>Responsible for scheduling, executing, pausing, and canceling tasks.
 * Supports concurrent execution of multiple tasks and streaming of task execution output.
 *
 * <p>Workflow:
 * <ol>
 *   <li>Periodically scan for tasks to be executed (status = submitted)</li>
 *   <li>Execute multiple tasks concurrently</li>
 *   <li>Stream output generated during task execution</li>
 *   <li>Update task status based on output type</li>
 * </ol>
 *
 * <p>Python reference: {@code modules/task_scheduler.py::TaskScheduler}
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class TaskScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TaskScheduler.class);

    private ControllerConfig config;
    private final TaskManager taskManager;
    private final ContextEngine contextEngine;
    private final AbilityManager abilityManager;
    private final EventQueue eventQueue;
    private final TaskExecutorRegistry taskExecutorRegistry;
    private final Map<String, Session> sessions;
    private final AgentCard card;

    // Scheduler running state
    private volatile boolean running;
    private Future<?> schedulerTask;

    // Running tasks: task_id -> (TaskExecutor, Future)
    private final Map<String, TaskEntry> runningTasks;

    // Lock for synchronized access
    private final ReentrantLock lock;

    // Executor services
    private final ExecutorService taskExecutorService;
    private ScheduledExecutorService schedulerExecutorService;

    /**
     * Constructs a TaskScheduler with all dependencies.
     *
     * @param config         the controller configuration
     * @param taskManager    the task manager
     * @param contextEngine  the context engine
     * @param abilityManager the ability manager
     * @param eventQueue     the event queue
     * @param card           the agent card
     */
    public TaskScheduler(ControllerConfig config,
                          TaskManager taskManager,
                          ContextEngine contextEngine,
                          AbilityManager abilityManager,
                          EventQueue eventQueue,
                          AgentCard card) {
        this.config = config;
        this.taskManager = taskManager;
        this.contextEngine = contextEngine;
        this.abilityManager = abilityManager;
        this.eventQueue = eventQueue;
        this.taskExecutorRegistry = new TaskExecutorRegistry();
        this.sessions = new ConcurrentHashMap<>();
        this.card = card;

        this.running = false;
        this.schedulerTask = null;
        this.runningTasks = new ConcurrentHashMap<>();
        this.lock = new ReentrantLock();

        this.taskExecutorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "TaskScheduler-executor");
            t.setDaemon(true);
            return t;
        });
    }

    // ==================== Properties ====================

    /**
     * Gets the configuration.
     *
     * @return the controller configuration
     */
    public ControllerConfig getConfig() {
        return config;
    }

    /**
     * Sets the configuration.
     *
     * @param config the new controller configuration
     */
    public void setConfig(ControllerConfig config) {
        this.config = config;
    }

    /**
     * Gets the session dictionary.
     *
     * @return the sessions map
     */
    public Map<String, Session> getSessions() {
        return sessions;
    }

    /**
     * Gets the task manager.
     *
     * @return the task manager
     */
    public TaskManager getTaskManager() {
        return taskManager;
    }

    /**
     * Gets the task executor registry.
     *
     * @return the task executor registry
     */
    public TaskExecutorRegistry getTaskExecutorRegistry() {
        return taskExecutorRegistry;
    }

    // ==================== Lifecycle ====================

    /**
     * Starts the task scheduler.
     *
     * <p>Starts the background scheduling task and begins periodic scanning
     * and execution of pending tasks.
     */
    public void start() {
        if (running) {
            logger.warn("TaskScheduler is already running");
            return;
        }
        running = true;
        schedulerExecutorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TaskScheduler-schedule");
            t.setDaemon(true);
            return t;
        });
        schedulerTask = schedulerExecutorService.submit(this::schedule);
        logger.info("TaskScheduler started");
    }

    /**
     * Stops the task scheduler.
     *
     * <p>Stops the background scheduling task and cancels all running tasks
     * to ensure clean shutdown.
     */
    public void stop() {
        if (!running) {
            logger.warn("TaskScheduler is not running");
            return;
        }

        running = false;

        // Cancel all running tasks
        if (!runningTasks.isEmpty()) {
            logger.info("Cancelling {} running tasks before stopping...", runningTasks.size());
            lock.lock();
            try {
                for (Map.Entry<String, TaskEntry> entry : runningTasks.entrySet()) {
                    TaskEntry taskEntry = entry.getValue();
                    if (taskEntry.future != null && !taskEntry.future.isDone()) {
                        taskEntry.future.cancel(true);
                        logger.info("Cancelled task {} successfully", entry.getKey());
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        if (schedulerTask != null) {
            schedulerTask.cancel(true);
        }

        if (schedulerExecutorService != null) {
            schedulerExecutorService.shutdownNow();
        }

        logger.info("TaskScheduler stopped");
    }

    // ==================== Schedule Loop ====================

    /**
     * Background scheduling loop.
     *
     * <p>Periodically scans TaskManager and concurrently executes tasks with
     * status SUBMITTED.
     */
    void schedule() {
        logger.info("TaskScheduler schedule loop started");
        while (running) {
            try {
                // 1. Get tasks to execute
                List<Task> submittedTasks = taskManager.getTask(
                    TaskFilter.builder().status(TaskStatus.SUBMITTED).build()
                );

                // 2. Concurrently start all new tasks (non-blocking)
                for (Task task : submittedTasks) {
                    // Check whether session exists
                    Session session = sessions.get(task.getSessionId());
                    if (session == null) {
                        logger.warn("Task {} session {} not found, skipping",
                            task.getTaskId(), task.getSessionId());
                        continue;
                    }

                    lock.lock();
                    try {
                        if (runningTasks.size() >= config.getMaxConcurrentTasks()) {
                            logger.warn("Reached max concurrent tasks limit ({}), waiting for next schedule",
                                config.getMaxConcurrentTasks());
                            break;
                        }

                        // Check whether it is already running
                        if (runningTasks.containsKey(task.getTaskId())) {
                            continue;
                        }

                        // Start non-blockingly
                        Future<?> execTask = taskExecutorService.submit(
                            () -> executeTaskWrapper(task.getTaskId(), session)
                        );

                        // Record in running tasks
                        runningTasks.put(task.getTaskId(), new TaskEntry(null, execTask));
                    } finally {
                        lock.unlock();
                    }

                    logger.info("Task {} ({}) started", task.getTaskId(), task.getTaskType());
                }

                // 3. Sleep briefly (loop remains non-blocking)
                Thread.sleep((long) (config.getScheduleInterval() * 1000));

            } catch (InterruptedException e) {
                logger.info("TaskScheduler schedule loop interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in schedule loop: {}", e.getMessage(), e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 4. When stopping, wait for all tasks to complete
        waitAllTasksComplete();
        logger.info("TaskScheduler schedule end");
    }

    // ==================== Task Execution ====================

    /**
     * Task execution wrapper.
     *
     * <p>Wraps executeTask to ensure exceptions are captured and handled correctly.
     *
     * @param taskId  the task ID
     * @param session the session object
     */
    void executeTaskWrapper(String taskId, Session session) {
        try {
            if (config.getTaskTimeout() != null) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> executeTask(taskId, session),
                    taskExecutorService
                );
                future.get(config.getTaskTimeout().longValue(), TimeUnit.SECONDS);
            } else {
                executeTask(taskId, session);
            }
        } catch (TimeoutException e) {
            String errorMsg = "Task timeout after " + config.getTaskTimeout() + " seconds";
            logger.error("Task {} {}", taskId, errorMsg);
            handleTaskExecutionFailure(taskId, session, errorMsg);
        } catch (CancellationException e) {
            logger.info("Task {} cancelled (likely by pause_task/cancel_task), "
                + "continuing gracefully to not affect other tasks", taskId);
        } catch (InterruptedException e) {
            logger.info("Task {} interrupted (likely by stop), continuing gracefully", taskId);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            logger.error("Task {} execution failed: {}", taskId, cause.getMessage(), cause);
            handleTaskExecutionFailure(taskId, session, cause.getMessage());
        } finally {
            // Cleanup: remove from running tasks
            lock.lock();
            try {
                runningTasks.remove(taskId);
            } finally {
                lock.unlock();
            }

            // Check if all tasks are done and send completion signal
            ensureSessionCompletionSignal(session.getSessionId());
        }
    }

    /**
     * Execute task.
     *
     * @param taskId  the task ID
     * @param session the session object
     */
    void executeTask(String taskId, Session session) {
        // 1. Get task object
        List<Task> tasks = taskManager.getTask(
            TaskFilter.builder().taskId(taskId).build()
        );
        if (tasks.isEmpty()) {
            logger.error("Task {} not found", taskId);
            throw ErrorBuilder.build(
                StatusCode.AGENT_CONTROLLER_TASK_EXECUTION_ERROR,
                "task " + taskId + " not found"
            );
        }
        Task task = tasks.get(0);

        logger.info("Executing task {} (type: {})", taskId, task.getTaskType());

        // 2. Create TaskExecutor
        TaskExecutorDependencies dependencies = new TaskExecutorDependencies(
            config, abilityManager, contextEngine, taskManager, eventQueue
        );
        TaskExecutor executor = taskExecutorRegistry.getTaskExecutor(
            task.getTaskType(), dependencies
        );

        // Update running task records (add executor)
        lock.lock();
        try {
            if (runningTasks.containsKey(taskId)) {
                TaskEntry entry = runningTasks.get(taskId);
                runningTasks.put(taskId, new TaskEntry(executor, entry.future));
            }
        } finally {
            lock.unlock();
        }

        // 3. Update task status to WORKING
        taskManager.updateTaskStatus(taskId, TaskStatus.WORKING);

        // 4. Execute task in streaming mode
        Iterator<ControllerOutputChunk> chunks = executor.executeAbility(taskId, session);
        while (chunks.hasNext()) {
            ControllerOutputChunk chunk = chunks.next();

            // 4.1 Write to session stream
            session.writeStream(chunk).join();

            // 4.2 Check output type and decide whether to stop the task
            if (chunk.getPayload() != null && chunk.getPayload().getType() != null) {
                String payloadType = chunk.getPayload().getType();

                // Task completed
                if (payloadType.equals(EventType.TASK_COMPLETION.getValue())) {
                    logger.info("Task {} completed", taskId);
                    taskManager.updateTaskStatus(taskId, TaskStatus.COMPLETED);
                    publishTaskEvent(taskId, session, chunk);
                    break;
                }

                // Task requires interaction
                if (payloadType.equals(EventType.TASK_INTERACTION.getValue())) {
                    logger.info("Task {} requires interaction", taskId);
                    taskManager.updateTaskStatus(taskId, TaskStatus.INPUT_REQUIRED);
                    publishTaskEvent(taskId, session, chunk);
                    break;
                }

                // Task failed
                if (payloadType.equals(EventType.TASK_FAILED.getValue())) {
                    logger.error("Task {} failed", taskId);
                    taskManager.updateTaskStatus(taskId, TaskStatus.FAILED);
                    publishTaskEvent(taskId, session, chunk);
                    break;
                }

                // Processing (continue execution)
                if ("processing".equals(payloadType)) {
                    continue;
                }
            }
        }
    }

    // ==================== Task Event Publishing ====================

    /**
     * Publishes a task event (auto-recognizes type from chunk).
     *
     * @param taskId  the task ID
     * @param session the session object
     * @param chunk   the output chunk
     */
    void publishTaskEvent(String taskId, Session session, ControllerOutputChunk chunk) {
        if (chunk.getPayload() == null || chunk.getPayload().getType() == null) {
            logger.error("Invalid chunk for task {}: missing payload or type", taskId);
            return;
        }

        List<Task> tasks = taskManager.getTask(
            TaskFilter.builder().taskId(taskId).build()
        );
        if (tasks.isEmpty()) {
            logger.error("Task {} not found in TaskManager", taskId);
            return;
        }
        Task task = tasks.get(0);
        String payloadType = chunk.getPayload().getType();
        List<BaseDataFrame> payloadData = chunk.getPayload().getData();

        // Automatically construct event based on payload type
        Event event;
        if (payloadType.equals(EventType.TASK_COMPLETION.getValue())) {
            event = new TaskCompletionEvent(payloadData, task);
        } else if (payloadType.equals(EventType.TASK_INTERACTION.getValue())) {
            event = new TaskInteractionEvent(payloadData, task);
        } else if (payloadType.equals(EventType.TASK_FAILED.getValue())) {
            String errorMsg = (payloadData != null && !payloadData.isEmpty()
                && payloadData.get(0) instanceof TextDataFrame)
                ? ((TextDataFrame) payloadData.get(0)).getText()
                : "Unknown error";
            task.setErrorMessage(errorMsg);
            event = new TaskFailedEvent(errorMsg, task);
        } else {
            logger.error("Unsupported payload type: {}", payloadType);
            return;
        }

        eventQueue.publishEvent(card.getId(), session, event);
        logger.info("Published {} for task {}", payloadType, taskId);
    }

    // ==================== Pause / Cancel ====================

    /**
     * Pauses a task.
     *
     * @param taskId the task ID
     * @return true if the task was successfully paused
     */
    public boolean pauseTask(String taskId) {
        List<Task> tasks = taskManager.getTask(
            TaskFilter.builder().taskId(taskId).build()
        );
        if (tasks.isEmpty()) {
            logger.error("Task {} not found in TaskManager", taskId);
            return false;
        }
        Task task = tasks.get(0);

        Session session = sessions.get(task.getSessionId());
        if (session == null) {
            logger.error("Session {} not found for task {}", task.getSessionId(), taskId);
            return false;
        }

        lock.lock();
        try {
            // Check whether the task is running
            if (!runningTasks.containsKey(taskId)) {
                logger.warn("Task {} is not running, cannot pause", taskId);
                return false;
            }

            // Get task and executor
            TaskEntry entry = runningTasks.get(taskId);
            TaskExecutor executor = entry.executor;

            // Check whether it can be paused
            if (executor != null) {
                try {
                    TaskExecutor.PauseResult pauseResult = executor.canPause(taskId, session);
                    if (!pauseResult.canPause()) {
                        logger.warn("Task {} cannot be paused, reason: {}", taskId, pauseResult.reason());
                        return false;
                    }

                    // Execute executor pause logic
                    executor.pause(taskId, session);
                } catch (Exception e) {
                    logger.error("Error pausing task {}: {}", taskId, e.getMessage(), e);
                    return false;
                }
            }

            // Cancel the running future
            if (entry.future != null && !entry.future.isDone()) {
                entry.future.cancel(true);
            }

            // Clean up running task records
            runningTasks.remove(taskId);
        } finally {
            lock.unlock();
        }

        // Update task status
        taskManager.updateTaskStatus(taskId, TaskStatus.PAUSED);
        logger.info("Task {} paused successfully", taskId);
        return true;
    }

    /**
     * Cancels a task.
     *
     * @param taskId the task ID
     * @return true if the task was successfully canceled
     */
    public boolean cancelTask(String taskId) {
        List<Task> tasks = taskManager.getTask(
            TaskFilter.builder().taskId(taskId).build()
        );
        if (tasks.isEmpty()) {
            logger.error("Task {} not found in TaskManager", taskId);
            return false;
        }
        Task task = tasks.get(0);

        Session session = sessions.get(task.getSessionId());
        if (session == null) {
            logger.error("Session {} not found for task {}", task.getSessionId(), taskId);
            return false;
        }

        lock.lock();
        try {
            // Check whether the task is running
            if (!runningTasks.containsKey(taskId)) {
                logger.warn("Task {} is not running, cannot cancel", taskId);
                return false;
            }

            // Get task executor
            TaskEntry entry = runningTasks.get(taskId);
            TaskExecutor executor = entry.executor;

            // Check whether it can be canceled
            if (executor != null) {
                try {
                    TaskExecutor.CancelResult cancelResult = executor.canCancel(taskId, session);
                    if (!cancelResult.canCancel()) {
                        logger.warn("Task {} cannot be cancelled: {}", taskId, cancelResult.reason());
                        return false;
                    }

                    // Execute executor cancel logic
                    executor.cancel(taskId, session);
                } catch (Exception e) {
                    logger.error("Error cancelling task {}: {}", taskId, e.getMessage(), e);
                    return false;
                }
            }

            // Cancel the running future
            if (entry.future != null && !entry.future.isDone()) {
                entry.future.cancel(true);
            }

            // Clean up running task records
            runningTasks.remove(taskId);
        } finally {
            lock.unlock();
        }

        // Update task status
        taskManager.updateTaskStatus(taskId, TaskStatus.CANCELED);
        logger.info("Task {} cancelled successfully", taskId);
        return true;
    }

    // ==================== Completion Check ====================

    /**
     * Checks if all tasks for a session are in terminal status.
     *
     * @param sessionId the session ID
     * @return true if all tasks are completed
     */
    boolean areAllTasksCompleted(String sessionId) {
        try {
            List<Task> sessionTasks = taskManager.getTask(
                TaskFilter.builder().sessionId(sessionId).build()
            );
            if (sessionTasks.isEmpty()) {
                logger.warn("No tasks found for session {}", sessionId);
                return false;
            }

            // Check if any task is still actively working or submitted
            Set<TaskStatus> activeStates = Set.of(TaskStatus.SUBMITTED, TaskStatus.WORKING);
            boolean hasActiveTasks = sessionTasks.stream()
                .anyMatch(t -> activeStates.contains(t.getStatus()));

            if (!hasActiveTasks) {
                logger.info("No active tasks for session {}", sessionId);
                return true;
            }

            return false;
        } catch (Exception e) {
            logger.error("Error checking task completion status: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Ensures completion signal is sent if all tasks are done.
     *
     * @param sessionId the session ID
     */
    void ensureSessionCompletionSignal(String sessionId) {
        try {
            if (!areAllTasksCompleted(sessionId)) {
                logger.info("not all tasks completed, continue");
                return;
            }

            logger.info("All tasks completed for session {}, sending completion signal", sessionId);

            Session session = sessions.get(sessionId);
            if (session == null) {
                logger.warn("Session {} not found, cannot send completion signal", sessionId);
                return;
            }

            // Build and send completion message
            ControllerOutputChunk completionChunk = new ControllerOutputChunk(
                0,
                "controller_output",
                new ControllerOutputPayload(
                    "all_tasks_processed",
                    List.of(new TextDataFrame("All tasks have been successfully processed")),
                    null
                ),
                true
            );

            session.writeStream(completionChunk).join();
            logger.info("Completion signal sent for session {}", sessionId);

        } catch (Exception e) {
            logger.error("Unexpected error in ensureSessionCompletionSignal: {}", e.getMessage(), e);
        }
    }

    // ==================== Private Helpers ====================

    /**
     * Handles task failure by updating status and publishing failure event.
     */
    private void handleTaskExecutionFailure(String taskId, Session session, String errorMessage) {
        taskManager.updateTaskStatus(taskId, TaskStatus.FAILED);

        ControllerOutputChunk failedChunk = new ControllerOutputChunk(
            0,
            "controller_output",
            new ControllerOutputPayload(
                EventType.TASK_FAILED.getValue(),
                List.of(new TextDataFrame(errorMessage)),
                null
            ),
            false
        );
        publishTaskEvent(taskId, session, failedChunk);
    }

    /**
     * Waits for all running tasks to complete.
     */
    private void waitAllTasksComplete() {
        lock.lock();
        List<Future<?>> futures;
        try {
            if (runningTasks.isEmpty()) {
                return;
            }

            logger.info("Waiting for {} running tasks to complete...", runningTasks.size());

            futures = new ArrayList<>();
            for (TaskEntry entry : runningTasks.values()) {
                if (entry.future != null) {
                    futures.add(entry.future);
                }
            }
        } finally {
            lock.unlock();
        }

        if (!futures.isEmpty()) {
            int successCount = 0;
            int errorCount = 0;
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    successCount++;
                } catch (Exception e) {
                    errorCount++;
                }
            }
            logger.info("All tasks completed: {} succeeded, {} failed", successCount, errorCount);
        }

        lock.lock();
        try {
            runningTasks.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Internal record for tracking running tasks.
     */
    record TaskEntry(TaskExecutor executor, Future<?> future) {}

    // ==================== Package-private accessors for testing ====================

    Map<String, TaskEntry> getRunningTasks() {
        return runningTasks;
    }

    boolean isRunning() {
        return running;
    }

    Future<?> getSchedulerTask() {
        return schedulerTask;
    }
}

