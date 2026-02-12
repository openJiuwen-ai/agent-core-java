// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.contextengine.ContextEngine;
import com.openjiuwen.core.controller.modules.*;
import com.openjiuwen.core.controller.schema.*;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Controller.
 *
 * <p>Responsible for handling events and managing the task lifecycle.
 * It is the core component of ControllerAgent.
 *
 * <p>Main responsibilities:
 * <ul>
 *   <li>Initialize and wire sub-components (TaskManager, EventQueue, TaskScheduler)</li>
 *   <li>Register/remove task executors</li>
 *   <li>Start/stop the event processing pipeline</li>
 *   <li>Execute batch (invoke) and streaming (stream) workflows</li>
 *   <li>Persist and restore task manager state</li>
 * </ul>
 *
 * <p>Python reference: {@code controller/base.py::Controller}
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private AgentCard card;
    private AbilityManager abilityManager;
    private ControllerConfig config;
    private ContextEngine contextEngine;
    private TaskManager taskManager;
    private EventQueue eventQueue;
    private TaskScheduler taskScheduler;
    private EventHandler eventHandler;

    // Track whether controller has been started
    private volatile boolean started;

    /**
     * Default constructor.
     */
    public Controller() {
        this.started = false;
    }

    /**
     * Initializes the controller with all dependencies.
     *
     * <p>Creates TaskManager, EventQueue, and TaskScheduler sub-components.
     *
     * @param card           the agent card
     * @param config         the controller configuration
     * @param abilityManager the ability manager
     * @param contextEngine  the context engine
     */
    public void init(AgentCard card, ControllerConfig config,
                     AbilityManager abilityManager, ContextEngine contextEngine) {
        this.card = card;
        this.config = config;
        this.abilityManager = abilityManager;
        this.contextEngine = contextEngine;
        this.taskManager = new TaskManager(config);
        this.eventQueue = new EventQueue(config);
        this.taskScheduler = new TaskScheduler(
            config,
            taskManager,
            contextEngine,
            abilityManager,
            eventQueue,
            card
        );
    }

    /**
     * Initializes the controller (overload for backward compatibility with ControllerAgent).
     *
     * @param card           the agent card
     * @param config         the controller configuration
     * @param abilityManager the ability manager
     * @param contextEngine  the context engine
     * @param messageQueue   the message queue (ignored, kept for API compatibility)
     */
    public void init(AgentCard card, ControllerConfig config,
                     AbilityManager abilityManager, ContextEngine contextEngine,
                     Object messageQueue) {
        init(card, config, abilityManager, contextEngine);
    }

    // ==================== Properties ====================

    /**
     * Gets the agent card.
     *
     * @return the agent card, or null if not initialized
     */
    public AgentCard getCard() {
        return card;
    }

    /**
     * Gets the event queue.
     *
     * @return the event queue, or null if not initialized
     */
    public EventQueue getEventQueue() {
        return eventQueue;
    }

    /**
     * Gets the controller configuration.
     *
     * @return the configuration, or null if not initialized
     */
    public ControllerConfig getConfig() {
        return config;
    }

    /**
     * Sets the controller configuration.
     *
     * <p>Propagates the new configuration to all sub-components.
     *
     * @param config the new configuration
     */
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

    /**
     * Gets the context engine.
     *
     * @return the context engine
     */
    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    /**
     * Sets the context engine.
     *
     * @param contextEngine the new context engine
     */
    public void setContextEngine(ContextEngine contextEngine) {
        this.contextEngine = contextEngine;
    }

    /**
     * Gets the ability manager.
     *
     * @return the ability manager
     */
    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    /**
     * Sets the ability manager.
     *
     * @param abilityManager the new ability manager
     */
    public void setAbilityManager(AbilityManager abilityManager) {
        this.abilityManager = abilityManager;
    }

    /**
     * Gets the task manager.
     *
     * @return the task manager, or null if not initialized
     */
    public TaskManager getTaskManager() {
        return taskManager;
    }

    /**
     * Gets the task scheduler.
     *
     * @return the task scheduler, or null if not initialized
     */
    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }

    // ==================== Event Handler ====================

    /**
     * Sets the event handler.
     *
     * <p>Wires the handler with config, context_engine, task_scheduler, task_manager,
     * and ability_manager.
     *
     * @param eventHandler the event handler instance
     */
    public void setEventHandler(EventHandler eventHandler) {
        this.eventHandler = eventHandler;
        this.eventHandler.setConfig(this.config);
        this.eventHandler.setContextEngine(this.contextEngine);
        this.eventHandler.setTaskScheduler(this.taskScheduler);
        this.eventHandler.setTaskManager(this.taskManager);
        this.eventHandler.setAbilityManager(this.abilityManager);
    }

    // ==================== Task Executor Registration ====================

    /**
     * Adds a task executor.
     *
     * @param taskType            the task type string
     * @param taskExecutorBuilder builder function that creates a TaskExecutor from dependencies
     * @return this controller (supports chaining)
     */
    public Controller addTaskExecutor(
            String taskType,
            Function<TaskExecutorDependencies, TaskExecutor> taskExecutorBuilder) {
        taskScheduler.getTaskExecutorRegistry().addTaskExecutor(taskType, taskExecutorBuilder);
        return this;
    }

    /**
     * Removes a task executor.
     *
     * @param taskType the task type string
     */
    public void removeTaskExecutor(String taskType) {
        taskScheduler.getTaskExecutorRegistry().removeTaskExecutor(taskType);
    }

    // ==================== State Persistence ====================

    /**
     * Restores TaskManager state from session.
     *
     * <p>If restoration fails, clears the current task_manager state without
     * raising errors so that new tasks are not affected.
     *
     * @param session the session object
     * @return true if restored successfully, false if there was no state or restoration failed
     */
    @SuppressWarnings("unchecked")
    public boolean restoreTaskManagerState(Session session) {
        Object controllerStateObj = session.getState("controller");

        if (controllerStateObj == null) {
            // No saved state, clear all task manager state
            logger.info("No saved state found for session {}, clearing task manager", session.getSessionId());
            taskManager.clearState();
            return false;
        }

        Map<String, Object> controllerState;
        try {
            controllerState = (Map<String, Object>) controllerStateObj;
        } catch (ClassCastException e) {
            logger.error("Controller state is not a Map for session {}, clearing task manager",
                session.getSessionId());
            taskManager.clearState();
            return false;
        }

        if (!controllerState.containsKey("task_manager_state")) {
            logger.info("No task_manager_state in controller state for session {}, clearing task manager",
                session.getSessionId());
            taskManager.clearState();
            return false;
        }

        try {
            logger.info("Restoring TaskManager state for session {}", session.getSessionId());

            Object stateObj = controllerState.get("task_manager_state");
            TaskManagerState taskManagerState;

            if (stateObj instanceof TaskManagerState tms) {
                taskManagerState = tms;
            } else {
                // State is not a valid TaskManagerState object
                throw new IllegalArgumentException("Invalid task_manager_state type: "
                    + (stateObj != null ? stateObj.getClass().getName() : "null"));
            }

            // Load state into task_manager
            taskManager.loadState(taskManagerState);
            logger.info("Successfully restored TaskManager state: {} tasks, {} root tasks",
                taskManagerState.getTasks().size(),
                taskManagerState.getRootTasks().size());
            return true;

        } catch (Exception e) {
            logger.error("Failed to restore TaskManager state for session {}: {}, "
                + "clearing task manager state instead",
                session.getSessionId(), e.getMessage(), e);
            // Fallback: clear all task manager state
            taskManager.clearState();
            return false;
        }
    }

    /**
     * Saves TaskManager state to session.
     *
     * @param session the session object
     */
    public void saveTaskManagerState(Session session) {
        if (!config.isEnableTaskPersistence()) {
            logger.info("Task persistence disabled, no need to save TaskManager state for session");
            return;
        }
        try {
            TaskManagerState taskManagerState = taskManager.getState();
            Map<String, Object> controllerState = new HashMap<>();
            controllerState.put("task_manager_state", taskManagerState);

            // Clear old state first, then update with new state
            Map<String, Object> clearMap = new HashMap<>();
            clearMap.put("controller", null);
            session.updateState(clearMap);

            Map<String, Object> saveMap = new HashMap<>();
            saveMap.put("controller", controllerState);
            session.updateState(saveMap);

            logger.info("Saved TaskManager state for session {}: {} tasks, {} root tasks",
                session.getSessionId(),
                taskManagerState.getTasks().size(),
                taskManagerState.getRootTasks().size());
        } catch (Exception e) {
            logger.error("Failed to save TaskManager state for session {}: {}",
                session.getSessionId(), e.getMessage());
        }
    }

    // ==================== Lifecycle ====================

    /**
     * Starts the controller.
     *
     * <p>Starts the event queue and task scheduler to begin processing tasks.
     */
    public void start() {
        eventQueue.start();
        taskScheduler.start();
        started = true;
    }

    /**
     * Stops the controller.
     *
     * <p>Stops the task scheduler and event queue, and stops processing tasks.
     */
    public void stop() {
        taskScheduler.stop();
        eventQueue.stop();
        started = false;
    }

    // ==================== Execution ====================

    /**
     * Batch execution using controller.
     *
     * <p>Calls stream method and converts streamed messages into a batch output.
     *
     * @param inputEvent the input event
     * @param session    the session object
     * @param streamModes the stream output modes (optional, can be null)
     * @return a CompletableFuture containing the ControllerOutput
     */
    public CompletableFuture<ControllerOutput> invoke(InputEvent inputEvent, Session session,
                                                       List<StreamMode> streamModes) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Collect all streamed output chunks
                List<ControllerOutputChunk> chunks = new ArrayList<>();
                Iterator<ControllerOutputChunk> streamIter = stream(inputEvent, session, streamModes, null);
                while (streamIter.hasNext()) {
                    chunks.add(streamIter.next());
                }

                return new ControllerOutput(EventType.TASK_COMPLETION.getValue(), chunks, null);

            } catch (BaseError e) {
                throw e;
            } catch (Exception e) {
                logger.error("Controller invoke error: {}", e.getMessage(), e);
                throw ErrorBuilder.build(
                    StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    e.getMessage(),
                    null,
                    e,
                    Map.of("error_msg", e.getMessage())
                );
            }
        });
    }

    /**
     * Stream execution using controller.
     *
     * <p>Handles the full lifecycle:
     * <ol>
     *   <li>Lazy start (if not already started)</li>
     *   <li>Restore controller state (including task_manager state)</li>
     *   <li>Register session with task_scheduler</li>
     *   <li>Subscribe to event types via event_queue</li>
     *   <li>Publish input event to trigger event handling and task scheduling</li>
     *   <li>Read event handling results from session stream and iterate them</li>
     *   <li>Deactivate all subscriptions</li>
     *   <li>Save controller state (including task_manager state)</li>
     *   <li>Remove session from task_scheduler</li>
     * </ol>
     *
     * @param inputEvent   the input event
     * @param session      the session object
     * @param streamModes  the stream modes (optional, can be null)
     * @param messageQueue the message queue (optional, ignored)
     * @return an iterator over ControllerOutputChunk
     */
    public Iterator<ControllerOutputChunk> stream(InputEvent inputEvent, Session session,
                                                   List<StreamMode> streamModes,
                                                   Object messageQueue) {
        // Lazy start
        if (!started) {
            eventQueue.start();
            eventQueue.setEventHandler(eventHandler);
            taskScheduler.start();
            started = true;
            logger.info("Controller started (lazy start)");
        }

        String agentId = card.getId();
        String sessionId = session.getSessionId();

        // Restore controller state
        boolean stateRestored = restoreTaskManagerState(session);
        if (!stateRestored) {
            logger.info("Starting with clean TaskManager state for session {}", sessionId);
        }

        // Register session
        taskScheduler.getSessions().put(sessionId, session);

        List<ControllerOutputChunk> collectedChunks = new ArrayList<>();
        try {
            // Subscribe to 4 event types
            eventQueue.subscribe(agentId, sessionId);

            // Publish input event to trigger event handling and task scheduling
            eventQueue.publishEvent(agentId, session, inputEvent);

            // Read from session stream and collect chunks
            Iterable<Object> streamIterable = getStreamIterable(session);
            for (Object rawChunk : streamIterable) {
                if (rawChunk instanceof ControllerOutputChunk chunk) {
                    // Check if this is a completion message
                    if (chunk.getPayload() != null
                        && "all_tasks_processed".equals(chunk.getPayload().getType())) {
                        logger.info("All tasks handled for session {}, stopping stream", sessionId);
                        break;
                    }
                    collectedChunks.add(chunk);
                }
            }

        } catch (BaseError e) {
            throw e;
        } catch (Exception e) {
            logger.error("Controller runtime error: {}", e.getMessage());
            throw ErrorBuilder.build(
                StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                e.getMessage(),
                null,
                e,
                Map.of("error_msg", e.getMessage())
            );
        } finally {
            // Save controller state
            saveTaskManagerState(session);

            // Unsubscribe events
            eventQueue.unsubscribe(agentId, sessionId);

            // Remove session
            taskScheduler.getSessions().remove(sessionId);

            // Keep Controller running
            logger.info("Session {} completed, {} active sessions remaining",
                sessionId, taskScheduler.getSessions().size());
        }

        return collectedChunks.iterator();
    }

    /**
     * Gets the stream iterable from a session.
     *
     * <p>This method extracts the stream iterator from the session. It handles
     * both TaskSession (which has streamIterator()) and generic Session.
     *
     * @param session the session
     * @return an iterable of objects from the session stream
     */
    private Iterable<Object> getStreamIterable(Session session) {
        try {
            // Use reflection to call streamIterator() if available
            var method = session.getClass().getMethod("streamIterator");
            @SuppressWarnings("unchecked")
            Iterable<Object> result = (Iterable<Object>) method.invoke(session);
            return result != null ? result : Collections.emptyList();
        } catch (Exception e) {
            logger.warn("Session does not support streamIterator(): {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
