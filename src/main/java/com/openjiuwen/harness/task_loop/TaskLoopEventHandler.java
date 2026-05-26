/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * EventHandler that drives the outer task loop.
 *
 * <p>Routes core EventQueue events through the TaskScheduler
 * pipeline and updates TaskPlan state accordingly.
 *
 * <p>Uses a per-round Future pattern: each iteration of
 * the outer loop creates a new Future via prepareRound(),
 * and completion/failed/abort events resolve that Future.
 * A monotonic roundId prevents stale completions from
 * resolving the wrong Future.
 *
 * <p>Mirrors Python's {@code TaskLoopEventHandler} in
 * {@code openjiuwen.harness.task_loop.task_loop_event_handler}.
 */
public class TaskLoopEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TaskLoopEventHandler.class);
    
    /** Task type for deep agent tasks. */
    public static final String DEEP_TASK_TYPE = "deep_agent_task";

    private final Object deepAgent;
    private final AtomicInteger roundId = new AtomicInteger(0);
    private final AtomicReference<Map<String, Object>> lastResult = new AtomicReference<>(null);
    private final AtomicReference<CompletableFuture<Map<String, Object>>> currentFuture = new AtomicReference<>(null);
    private final LoopQueues interactionQueues;

    /**
     * Construct with deep agent reference.
     */
    public TaskLoopEventHandler(Object deepAgent) {
        this.deepAgent = deepAgent;
        this.interactionQueues = new LoopQueues();
    }

    /**
     * Default constructor.
     */
    public TaskLoopEventHandler() {
        this(null);
    }

    /**
     * Get interaction queues.
     */
    public LoopQueues getInteractionQueues() {
        return interactionQueues;
    }

    /**
     * Prepare a new round.
     *
     * <p>Creates a new Future that will be resolved when
     * the round completes or fails.
     */
    public String prepareRound() {
        int id = roundId.incrementAndGet();
        String roundIdStr = "round_" + id;

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        currentFuture.set(future);

        LOG.debug("[TaskLoopEventHandler] prepare_round round_id={}", roundIdStr);
        return roundIdStr;
    }

    /**
     * Wait for round completion.
     *
     * @param roundIdStr Round identifier for correlation
     * @param timeoutMs Timeout in milliseconds
     * @return CompletableFuture with round result
     */
    public CompletableFuture<Map<String, Object>> waitForRoundCompletion(String roundIdStr, long timeoutMs) {
        CompletableFuture<Map<String, Object>> future = currentFuture.get();
        if (future == null) {
            return CompletableFuture.completedFuture(null);
        }

        LOG.debug("[TaskLoopEventHandler] wait_for_round_completion round_id={}, timeout={}", roundIdStr, timeoutMs);

        return future.orTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .whenComplete((result, error) -> {
                    if (result != null) {
                        lastResult.set(result);
                        LOG.debug("[TaskLoopEventHandler] round completed round_id={}", roundIdStr);
                    }
                    if (error != null) {
                        LOG.warn("[TaskLoopEventHandler] round failed round_id={} error={}", roundIdStr, error.getMessage());
                    }
                });
    }

    /**
     * Get last result.
     */
    public Map<String, Object> getLastResult() {
        return lastResult.get();
    }

    /**
     * Handle input event.
     * <p>
     * Mirrors Python's {@code handle_input} method which creates a core Task for scheduling.
     */
    public void handleInputEvent(Object event) {
        LOG.debug("[TaskLoopEventHandler] handle_input_event event_type={}", event.getClass().getSimpleName());
        
        if (deepAgent instanceof com.openjiuwen.harness.DeepAgent da) {
            try {
                // Extract query from InputEvent
                String query = extractQuery(event);
                String taskId = extractTaskId(event);
                String sessionId = extractSessionId(event);
                
                // Get current round from event metadata
                int currentRound = roundId.get();
                
                // Resolve task_id from TaskPlan if available
                if (taskId == null) {
                    taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);
                }
                
                // Build task metadata
                Map<String, Object> taskMetadata = new HashMap<>();
                taskMetadata.put("_handler_round_id", currentRound);
                taskMetadata.put("run_kind", extractRunKind(event));
                taskMetadata.put("run_context", extractRunContext(event));
                taskMetadata.put("is_follow_up", extractIsFollowUp(event));
                
                // Create core task
                String createdTaskId = createTask(DEEP_TASK_TYPE, taskMetadata);
                
                LOG.info("[TaskLoopEventHandler] created task_id={} for query={}", createdTaskId, 
                    query != null && query.length() > 50 ? query.substring(0, 50) + "..." : query);
            } catch (Exception e) {
                LOG.error("[TaskLoopEventHandler] handle_input_event failed", e);
            }
        }
    }
    
/**
     * Extract query from event.
     */
    private String extractQuery(Object event) {
        if (event instanceof com.openjiuwen.core.controller.schema.InputEvent ie) {
            // Extract text from first TextDataFrame if available
            var inputData = ie.getInputData();
            if (inputData != null && !inputData.isEmpty()) {
                var firstFrame = inputData.get(0);
                if (firstFrame instanceof com.openjiuwen.core.controller.schema.DataFrame.TextDataFrame tf) {
                    return tf.text();
                }
                if (firstFrame instanceof com.openjiuwen.core.controller.schema.DataFrame.JsonDataFrame jf) {
                    Object query = jf.data().get("query");
                    return query != null ? query.toString() : null;
                }
            }
}
        return null;
    }
    
    /**
     * Extract task_id from event metadata.
     */
    private String extractTaskId(Object event) {
        if (event instanceof com.openjiuwen.core.controller.schema.InputEvent ie) {
            Map<String, Object> metadata = ie.getMetadata();
            if (metadata != null) {
                Object taskId = metadata.get("task_id");
                return taskId != null ? taskId.toString() : null;
            }
        }
        return null;
    }
    
    /**
     * Extract session_id from event.
     */
    private String extractSessionId(Object event) {
        if (event instanceof com.openjiuwen.core.controller.schema.InputEvent ie) {
            Map<String, Object> metadata = ie.getMetadata();
            if (metadata != null) {
                Object sessionId = metadata.get("session_id");
                return sessionId != null ? sessionId.toString() : "default";
            }
        }
        return "default";
    }
    
    /**
     * Extract run_kind from event metadata.
     */
    private String extractRunKind(Object event) {
        if (event instanceof com.openjiuwen.core.controller.schema.InputEvent ie) {
            Map<String, Object> metadata = ie.getMetadata();
            if (metadata != null) {
                Object runKind = metadata.get("run_kind");
                return runKind != null ? runKind.toString() : null;
            }
        }
        return null;
    }
    
    /**
     * Extract run_context from event metadata.
     */
    private String extractRunContext(Object event) {
        if (event instanceof com.openjiuwen.core.controller.schema.InputEvent ie) {
            Map<String, Object> metadata = ie.getMetadata();
            if (metadata != null) {
                Object runContext = metadata.get("run_context");
                return runContext != null ? runContext.toString() : null;
            }
        }
        return null;
    }
    
    /**
     * Extract is_follow_up flag from event metadata.
     */
private boolean extractIsFollowUp(Object event) {
        if (event instanceof com.openjiuwen.core.controller.schema.InputEvent ie) {
            Map<String, Object> metadata = ie.getMetadata();
            if (metadata != null) {
                Object isFollowUp = metadata.get("is_follow_up");
                return isFollowUp != null && Boolean.parseBoolean(isFollowUp.toString());
            }
        }
        return false;
    }
    
    /**
     * Create a core task.
     */
    private String createTask(String taskType, Map<String, Object> metadata) {
        String taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);
        LOG.info("[TaskLoopEventHandler] create_task task_id={} type={}", taskId, taskType);
        
        if (deepAgent instanceof com.openjiuwen.harness.DeepAgent da) {
            try {
                com.openjiuwen.core.common.task_manager.TaskManager taskManager = 
                    com.openjiuwen.core.common.task_manager.TaskManager.getInstance();
                
                if (taskManager != null) {
                    Map<String, Object> taskMeta = new HashMap<>(metadata);
                    taskManager.createTask(
                        () -> null,
                        taskId,
                        taskType,
                        null,
                        null,
                        taskMeta,
                        true
                    );
                    LOG.debug("[TaskLoopEventHandler] task added to manager task_id={}", taskId);
                }
            } catch (Exception e) {
                LOG.error("[TaskLoopEventHandler] create_task failed", e);
            }
        }
        
        return taskId;
    }
}