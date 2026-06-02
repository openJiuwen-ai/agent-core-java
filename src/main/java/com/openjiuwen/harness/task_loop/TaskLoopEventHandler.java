/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskCompletionEvent;
import com.openjiuwen.core.controller.schema.TaskFailedEvent;
import com.openjiuwen.core.controller.schema.TaskInteractionEvent;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.AgentSessionApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
    private TaskManagerAdapter taskManager;

    /**
     * Minimal adapter used by tests and the task-loop bridge.
     */
    public interface TaskManagerAdapter {
        void addTask(Task task);
    }

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

    public void setTaskManager(TaskManagerAdapter taskManager) {
        this.taskManager = taskManager;
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
     * Mirrors Python's {@code wait_completion()} helper.
     */
    public Map<String, Object> waitCompletion(long timeoutMs) {
        CompletableFuture<Map<String, Object>> future = currentFuture.get();
        if (future == null) {
            return Map.of("error", "no active round");
        }
        try {
            Map<String, Object> result = timeoutMs > 0
                    ? future.get(timeoutMs, TimeUnit.MILLISECONDS)
                    : future.get();
            if (result == null || result.isEmpty()) {
                result = Map.of("status", "completed");
            }
            lastResult.set(result);
            return result;
        } catch (java.util.concurrent.TimeoutException e) {
            Map<String, Object> result = Map.of("error", "completion_timeout");
            lastResult.set(result);
            return result;
        } catch (java.util.concurrent.CancellationException e) {
            Map<String, Object> result = Map.of("error", "cancelled");
            lastResult.set(result);
            return result;
        } catch (Exception e) {
            Map<String, Object> result = Map.of("error", e.getMessage() != null ? e.getMessage() : "unknown");
            lastResult.set(result);
            return result;
        }
    }

    /**
     * Get last result.
     */
    public Map<String, Object> getLastResult() {
        return lastResult.get();
    }

    /**
     * Resolve the current future if the round matches.
     */
    public void resolveFuture(Map<String, Object> result, String roundIdStr) {
        if (roundIdStr == null || !roundIdStr.startsWith("round_")) {
            return;
        }
        try {
            int parsed = Integer.parseInt(roundIdStr.substring("round_".length()));
            resolveFuture(result, parsed);
        } catch (NumberFormatException ignored) {
            // Ignore malformed round ids for parity with Python's stale-result guard.
        }
    }

    public void resolveFuture(Map<String, Object> result, int expectedRoundId) {
        if (expectedRoundId != roundId.get()) {
            LOG.warn("Stale resolve: round_id={} != current={}, discarding result", expectedRoundId, roundId.get());
            return;
        }
        CompletableFuture<Map<String, Object>> future = currentFuture.get();
        if (future != null && !future.isDone()) {
            future.complete(result);
        }
    }

    /**
     * Python parity helper for input submission.
     */
    public Map<String, Object> handleInput(EventHandlerInput inputs) {
        if (inputs == null || !(inputs.getEvent() instanceof InputEvent event)) {
            return Map.of("status", "failed");
        }
        String roundIdStr = event.getMetadata() != null
                ? String.valueOf(event.getMetadata().getOrDefault("_handler_round_id", "round_" + roundId.get()))
                : "round_" + roundId.get();

        if (!(deepAgent instanceof com.openjiuwen.harness.DeepAgent)) {
            resolveFuture(Map.of("error", "no LoopCoordinator"), roundIdStr);
            return Map.of("status", "failed");
        }
        Object coordinator = lookupField(deepAgent, "loopCoordinator");
        if (coordinator == null) {
            coordinator = lookupField(deepAgent, "_loopCoordinator");
        }
        if (!(coordinator instanceof LoopCoordinator)) {
            resolveFuture(Map.of("error", "no LoopCoordinator"), roundIdStr);
            return Map.of("status", "failed");
        }

        String taskId = event.getMetadata() != null ? asString(event.getMetadata().get("task_id")) : null;
        boolean isFollowUp = event.getMetadata() != null
                && Boolean.parseBoolean(String.valueOf(event.getMetadata().getOrDefault("is_follow_up", false)));
        if (taskId == null || taskId.isBlank()) {
            taskId = UUID.randomUUID().toString().replace("-", "");
        }

        Task task = new Task();
        AgentSessionApi session = inputs.getSession();
        task.setSessionId(session != null ? session.getSessionId() : "default");
        task.setTaskId(taskId);
        task.setTaskType(DEEP_TASK_TYPE);
        task.setDescription(extractQuery(event));
        task.setStatus(TaskStatus.SUBMITTED);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("_handler_round_id", roundIdStr);
        metadata.put("run_kind", event.getMetadata() != null ? event.getMetadata().get("run_kind") : null);
        metadata.put("run_context", event.getMetadata() != null ? event.getMetadata().get("run_context") : null);
        metadata.put("is_follow_up", isFollowUp);
        task.setMetadata(metadata);
        task.setInputs(List.of(event));

        if (taskManager == null) {
            resolveFuture(Map.of("error", "task_manager is None"), roundIdStr);
            return Map.of("status", "failed");
        }
        taskManager.addTask(task);
        return Map.of("status", "submitted", "task_id", taskId);
    }

    public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
        if (inputs == null || !(inputs.getEvent() instanceof TaskInteractionEvent event)) {
            return Map.of("status", "steer_injected", "msg", "");
        }
        String msg = "";
        if (event.getInteraction() != null && !event.getInteraction().isEmpty()) {
            DataFrame first = event.getInteraction().get(0);
            if (first instanceof DataFrame.TextDataFrame textFrame) {
                msg = textFrame.text();
            } else {
                msg = String.valueOf(first);
            }
        }
        if (!msg.isEmpty()) {
            interactionQueues.pushSteer(msg);
        }
        return Map.of("status", "steer_injected", "msg", msg);
    }

    public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
        if (inputs == null || !(inputs.getEvent() instanceof TaskCompletionEvent event)) {
            return Map.of("status", "completed");
        }
        String roundIdStr = event.getMetadata() != null
                ? String.valueOf(event.getMetadata().getOrDefault("_handler_round_id", "round_" + roundId.get()))
                : "round_" + roundId.get();
        Map<String, Object> result = new LinkedHashMap<>();
        if (event.getTaskResult() != null) {
            for (DataFrame frame : event.getTaskResult()) {
                if (frame instanceof DataFrame.JsonDataFrame jsonDataFrame) {
                    result.putAll(jsonDataFrame.data());
                    break;
                }
                if (frame instanceof DataFrame.TextDataFrame textDataFrame) {
                    result.put("output", textDataFrame.text());
                }
            }
        }
        resolveFuture(result, roundIdStr);
        return Map.of(
                "status", "completed",
                "task_id", event.getMetadata() != null ? asString(event.getMetadata().get("task_id")) : null);
    }

    public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
        if (inputs == null || !(inputs.getEvent() instanceof TaskFailedEvent event)) {
            return Map.of("status", "failed", "error", "unknown");
        }
        String roundIdStr = event.getMetadata() != null
                ? String.valueOf(event.getMetadata().getOrDefault("_handler_round_id", "round_" + roundId.get()))
                : "round_" + roundId.get();
        String error = event.getErrorMessage() != null ? event.getErrorMessage() : "unknown";
        resolveFuture(Map.of("error", error), roundIdStr);
        return Map.of(
                "status", "failed",
                "task_id", event.getMetadata() != null ? asString(event.getMetadata().get("task_id")) : null,
                "error", error);
    }

    public void onAbort() {
        resolveFuture(Map.of("error", "aborted"), roundId.get());
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

    private static String extractQuery(InputEvent event) {
        if (event == null || event.getInputData() == null) {
            return "";
        }
        for (DataFrame frame : event.getInputData()) {
            if (frame instanceof DataFrame.TextDataFrame textFrame) {
                return textFrame.text();
            }
            if (frame instanceof DataFrame.JsonDataFrame jsonDataFrame) {
                Object query = jsonDataFrame.data().get("query");
                return query != null ? String.valueOf(query) : String.valueOf(jsonDataFrame.data());
            }
        }
        return "";
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Object lookupField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException ignored) {
                return null;
            }
        }
        return null;
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
