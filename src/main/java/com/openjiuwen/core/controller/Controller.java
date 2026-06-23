/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.controller.modules.EventHandler;
import com.openjiuwen.core.controller.modules.EventQueue;
import com.openjiuwen.core.controller.modules.TaskExecutor;
import com.openjiuwen.core.controller.modules.TaskExecutorDependencies;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.modules.TaskManager;
import com.openjiuwen.core.controller.modules.TaskManagerState;
import com.openjiuwen.core.controller.modules.TaskScheduler;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Controller 鈥?the core component of ControllerAgent.
 * <p>
 * Responsible for handling events and managing the task lifecycle.
 * Coordinates event queue, task scheduler, and event handler.
 * <p>
 * Mirrors Python's {@code Controller} in
 * {@code openjiuwen/core/controller/base.py}.
 */
public class Controller {

    private BaseCard card;
    private Object abilityManager;
    private ControllerConfig config;
    private ContextEngine contextEngine;
    private TaskManager taskManager;
    private EventQueue eventQueue;
    private TaskScheduler taskScheduler;
    private EventHandler eventHandler;

    private volatile boolean started = false;

    public Controller() {
    }

    /**
     * Initialize controller with all required dependencies.
     *
     * @param card            agent card
     * @param config          controller configuration
     * @param abilityManager  ability manager
     * @param contextEngine   context engine
     */
    public void init(
            BaseCard card,
            ControllerConfig config,
            Object abilityManager,
            ContextEngine contextEngine
    ) {
        this.card = card;
        this.config = config;
        this.abilityManager = abilityManager;
        this.contextEngine = contextEngine;
        this.taskManager = new TaskManager(config);
        this.eventQueue = new EventQueue(config);
        this.taskScheduler = new TaskScheduler(
                config, taskManager, contextEngine, abilityManager, eventQueue, card
        );
    }

    // ==================== Properties ====================

    public EventQueue getEventQueue() {
        return eventQueue;
    }

    public ControllerConfig getConfig() {
        return config;
    }

    public void setConfig(ControllerConfig config) {
        this.config = config;
        if (taskManager != null) {
            taskManager.setConfig(config);
        }
        if (eventQueue != null) {
            eventQueue.setConfig(config);
        }
        if (taskScheduler != null) {
            taskScheduler.setConfig(config);
        }
        if (eventHandler != null) {
            eventHandler.setConfig(config);
        }
    }

    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    public void setContextEngine(ContextEngine contextEngine) {
        this.contextEngine = contextEngine;
    }

    public Object getAbilityManager() {
        return abilityManager;
    }

    public void setAbilityManager(Object abilityManager) {
        this.abilityManager = abilityManager;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }

    public EventHandler getEventHandler() {
        return eventHandler;
    }

    /**
     * Set event handler and wire its dependencies.
     *
     * @param eventHandler event handler instance
     */
    public void setEventHandler(EventHandler eventHandler) {
        this.eventHandler = eventHandler;
        eventHandler.setConfig(config);
        eventHandler.setContextEngine(contextEngine);
        eventHandler.setTaskScheduler(taskScheduler);
        eventHandler.setTaskManager(taskManager);
        eventHandler.setAbilityManager(abilityManager);
    }

    /**
     * Add a task executor builder (fluent API).
     *
     * @param taskType             task type
     * @param taskExecutorBuilder  builder function
     * @return this controller for chaining
     */
    public Controller addTaskExecutor(
            String taskType,
            Function<TaskExecutorDependencies, TaskExecutor> taskExecutorBuilder
    ) {
        taskScheduler.getTaskExecutorRegistry().addTaskExecutor(taskType, taskExecutorBuilder);
        return this;
    }

    /**
     * Remove a task executor.
     *
     * @param taskType task type
     */
    public void removeTaskExecutor(String taskType) {
        taskScheduler.getTaskExecutorRegistry().removeTaskExecutor(taskType);
    }

    /**
     * Get task executor for a given task type.
     *
     * @param taskType     task type
     * @param dependencies task executor dependencies
     * @return task executor instance
     */
    public TaskExecutor getTaskExecutor(String taskType, TaskExecutorDependencies dependencies) {
        return taskScheduler.getTaskExecutorRegistry().getTaskExecutor(taskType, dependencies);
    }

    // ==================== State Management ====================

    @SuppressWarnings("unchecked")
    private boolean restoreTaskManagerState(AgentSessionApi session) {
        Object controllerState = session.getState("controller");
        if (controllerState == null) {
            Loggers.CONTROLLER.info("No saved state found for session {}, clearing task manager",
                    session.getSessionId());
            clearTaskManagerStateForSession(session.getSessionId());
            return false;
        }

        try {
            Map<String, Object> stateMap = (Map<String, Object>) controllerState;
            Object tmState = stateMap.get("task_manager_state");
            if (tmState == null) {
                clearTaskManagerStateForSession(session.getSessionId());
                return false;
            }

            Loggers.CONTROLLER.info("Restoring TaskManager state for session {}", session.getSessionId());
            TaskManagerState taskManagerState = TaskManagerState.fromMap((Map<String, Object>) tmState);
            taskManager.loadState(taskManagerState);
            Loggers.CONTROLLER.info("Successfully restored TaskManager state: {} tasks, {} root tasks",
                    taskManagerState.getTasks().size(), taskManagerState.getRootTasks().size());
            return true;
        } catch (Exception e) {
            Loggers.CONTROLLER.error("Failed to restore TaskManager state for session {}: {}, clearing instead",
                    session.getSessionId(), e.getMessage());
            clearTaskManagerStateForSession(session.getSessionId());
            return false;
        }
    }

    private void clearTaskManagerStateForSession(String sessionId) {
        List<Task> sessionTasks = taskManager.getTask(TaskFilter.bySessionId(sessionId));
        if (sessionTasks.isEmpty()) {
            return;
        }
        taskManager.removeTask(TaskFilter.bySessionId(sessionId));
    }

    private void saveTaskManagerState(AgentSessionApi session) {
        if (!config.isEnableTaskPersistence()) {
            Loggers.CONTROLLER.info("Task persistence disabled, skipping save");
            return;
        }
        try {
            TaskManagerState state = taskManager.getState();
            Map<String, Object> controllerState = Map.of(
                    "task_manager_state", state.toMap()
            );
            // Clear old state first, then update with new state (ensures proper cleanup of nested dict keys)
            Map<String, Object> clearedControllerState = new LinkedHashMap<>();
            clearedControllerState.put("controller", null);
            session.updateState(clearedControllerState);
            session.updateState(Map.of("controller", controllerState));
            Loggers.CONTROLLER.info("Saved TaskManager state for session {}: {} tasks, {} root tasks",
                    session.getSessionId(), state.getTasks().size(), state.getRootTasks().size());
        } catch (Exception e) {
            Loggers.CONTROLLER.error("Failed to save TaskManager state for session {}: {}",
                    session.getSessionId(), e.getMessage());
        }
    }

    // ==================== Start / Stop ====================

    /**
     * Start controller (event queue + task scheduler).
     */
    public void start() {
        eventQueue.start();
        eventQueue.setEventHandler(eventHandler);
        taskScheduler.start();
        started = true;
    }

    /**
     * Stop controller.
     */
    public void stop() {
        taskScheduler.stop();
        eventQueue.stop();
        started = false;
    }

    // ==================== Invoke / Stream ====================

    /**
     * Batch execution: collect all stream output into a single result.
     *
     * @param inputs  input event
     * @param session session object
     * @return controller output result
     */
    public ControllerOutput invoke(InputEvent inputs, AgentSessionApi session) {
        try {
            List<Object> outputs = new ArrayList<>();
            Iterator<Object> it = stream(inputs, session, List.of(StreamMode.OUTPUT));
            while (it.hasNext()) {
                Object obj = it.next();
                if (obj instanceof ControllerOutputChunk chunk
                        && chunk.getControllerPayload() != null
                        && ControllerOutputPayload.ALL_TASKS_PROCESSED.equals(chunk.getControllerPayload().getType())) {
                    continue;
                }
                outputs.add(obj);
            }
            return new ControllerOutput(EventType.TASK_COMPLETION.getValue(), outputs);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            Loggers.CONTROLLER.error("Controller invoke error: {}", e.getMessage());
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", e.getMessage());
        }
    }

    /**
     * Stream execution.
     * <p>
     * Workflow:
     * <ol>
     *   <li>Restore controller state (including task_manager state)</li>
     *   <li>Register session with task_scheduler</li>
     *   <li>Subscribe to event types via event_queue</li>
     *   <li>Publish input event to trigger processing</li>
     *   <li>Read from session stream until all tasks complete</li>
     *   <li>Save controller state</li>
     *   <li>Unsubscribe and cleanup</li>
     * </ol>
     *
     * @param inputs      input event
     * @param session     session object
     * @param streamModes list of stream output modes (optional)
     * @return iterator over stream output
     */
    public Iterator<Object> stream(
            InputEvent inputs,
            AgentSessionApi session,
            List<StreamMode> streamModes
    ) {
        // Ensure started
        if (!started) {
            start();
            Loggers.CONTROLLER.info("Controller auto-started");
        }

        String agentId = card.getId();
        String sessionId = session.getSessionId();

        // Restore state
        boolean stateRestored = restoreTaskManagerState(session);
        if (!stateRestored) {
            Loggers.CONTROLLER.info("Starting with clean TaskManager state for session {}", sessionId);
        }

        // Register session
        taskScheduler.getSessions().put(sessionId, session);

        try {
            // Subscribe to events
            eventQueue.subscribe(agentId, sessionId);

            // Publish input event
            eventQueue.publishEvent(agentId, session, inputs);
            emitCompletionSignalIfIdle(session);

            // Return an iterator that reads from session stream
            @SuppressWarnings("deprecation")
            Iterator<Object> sessionStream = session.streamIterator();
            return new ControllerStreamIterator(
                    sessionStream, session, agentId, sessionId, this
            );
        } catch (RuntimeException e) {
            // Cleanup on error
            cleanup(agentId, sessionId, session);
            throw e;
        } catch (Exception e) {
            cleanup(agentId, sessionId, session);
            Loggers.CONTROLLER.error("Controller runtime error: {}", e.getMessage());
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", e.getMessage());
        }
    }

    private void cleanup(String agentId, String sessionId, AgentSessionApi session) {
        saveTaskManagerState(session);
        eventQueue.unsubscribe(agentId, sessionId);
        taskScheduler.getSessions().remove(sessionId);
        Loggers.CONTROLLER.info("Session {} completed, {} active sessions remaining",
                sessionId, taskScheduler.getSessions().size());
    }

    private void emitCompletionSignalIfIdle(AgentSessionApi session) {
        String sessionId = session.getSessionId();
        List<Task> sessionTasks = taskManager.getTask(TaskFilter.bySessionId(sessionId));
        boolean hasActiveTask = sessionTasks.stream().anyMatch(task ->
                task.getStatus() == TaskStatus.SUBMITTED || task.getStatus() == TaskStatus.WORKING);

        if (hasActiveTask) {
            return;
        }

        ControllerOutputChunk completionChunk = new ControllerOutputChunk(
                0,
                ControllerOutputPayload.allTasksProcessed("All tasks have been successfully processed"),
                true
        );
        session.writeStream(completionChunk);
        Loggers.CONTROLLER.info("Immediate completion signal sent for idle session {}", sessionId);
    }

    /**
     * Wrapping iterator that reads from session stream, detects completion,
     * and performs cleanup when done.
     */
    private static class ControllerStreamIterator implements Iterator<Object>, AutoCloseable {

        private final Iterator<Object> delegate;
        private final AgentSessionApi session;
        private final String agentId;
        private final String sessionId;
        private final Controller controller;

        private Object nextItem;
        private boolean done = false;

        ControllerStreamIterator(Iterator<Object> delegate, AgentSessionApi session,
                                 String agentId, String sessionId, Controller controller) {
            this.delegate = delegate;
            this.session = session;
            this.agentId = agentId;
            this.sessionId = sessionId;
            this.controller = controller;
        }

        @Override
        public boolean hasNext() {
            if (done) {
                return false;
            }
            if (nextItem != null) {
                return true;
            }
            // Try to get next item
            while (delegate.hasNext()) {
                Object item = delegate.next();

                // Check for completion signal
                if (item instanceof ControllerOutputChunk chunk) {
                    if (chunk.getControllerPayload() != null
                            && ControllerOutputPayload.ALL_TASKS_PROCESSED.equals(
                                    chunk.getControllerPayload().getType())) {
                        Loggers.CONTROLLER.info("All tasks handled for session {}, stopping stream", sessionId);
                        finishStream();
                        return false;
                    }
                }

                nextItem = item;
                return true;
            }

            // Stream ended naturally
            finishStream();
            return false;
        }

        @Override
        public Object next() {
            if (done || nextItem == null) {
                throw new java.util.NoSuchElementException();
            }
            Object result = nextItem;
            nextItem = null;
            return result;
        }

        private void finishStream() {
            if (!done) {
                done = true;
                controller.cleanup(agentId, sessionId, session);
            }
        }

        @Override
        public void close() {
            finishStream();
        }
    }
}

