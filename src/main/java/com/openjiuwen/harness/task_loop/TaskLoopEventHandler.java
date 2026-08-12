/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.controller.modules.EventHandler;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.FollowUpEvent;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskCompletionEvent;
import com.openjiuwen.core.controller.schema.TaskFailedEvent;
import com.openjiuwen.core.controller.schema.TaskInteractionEvent;
import com.openjiuwen.core.controller.schema.TaskStatus;

import java.lang.reflect.Method;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Event handler used by the DeepAgent task-loop controller.
 *
 * <p>Mirrors Python's {@code TaskLoopEventHandler} in
 * {@code openjiuwen/harness/task_loop/task_loop_event_handler.py}.</p>
 */
public class TaskLoopEventHandler extends EventHandler {

    private final Object deepAgent;
    private LoopQueues interactionQueues = new LoopQueues();
    private Map<String, Object> lastResult;
    private CompletableFuture<Map<String, Object>> currentFuture;
    private int roundId;
    private Object sessionToolkit;

    /**
     * Create an event handler with a deep agent reference.
     *
     * <p>Accepts either {@code com.openjiuwen.harness.DeepAgent} or
     * {@code com.openjiuwen.harness.deep_agent.DeepAgent}.</p>
     *
     * @param deepAgent the deep agent
     */
    public TaskLoopEventHandler(Object deepAgent) {
        this.deepAgent = deepAgent;
    }

    /**
     * Create an event handler with a task loop controller.
     *
     * @param controller the task loop controller
     */
    public TaskLoopEventHandler(TaskLoopController controller) {
        this.deepAgent = null;
    }

    public Map<String, Object> getLastResult() {
        return lastResult == null ? null : new LinkedHashMap<>(lastResult);
    }

    public LoopQueues getInteractionQueues() {
        return interactionQueues;
    }

    public void setInteractionQueues(LoopQueues interactionQueues) {
        this.interactionQueues = interactionQueues == null ? new LoopQueues() : interactionQueues;
    }

    public void setSessionToolkit(Object sessionToolkit) {
        this.sessionToolkit = sessionToolkit;
    }

    public Object getSessionToolkit() {
        return sessionToolkit;
    }

    @Override
    public synchronized int prepareRound() {
        if (currentFuture != null && !currentFuture.isDone()) {
            currentFuture.cancel(false);
        }
        roundId += 1;
        currentFuture = new CompletableFuture<>();
        lastResult = null;
        return roundId;
    }

    /**
     * Prepare a new round with session context.
     *
     * @param sessionId  the session id (ignored in this implementation)
     * @param isFollowUp whether this is a follow-up round
     * @return the new round id
     */
    public synchronized int prepareRound(String sessionId, boolean isFollowUp) {
        return prepareRound();
    }

    @Override
    public Map<String, Object> waitCompletion(Double timeout) {
        CompletableFuture<Map<String, Object>> future = currentFuture;
        if (future == null) {
            lastResult = resultMap("error", "no active round");
            return getLastResult();
        }

        Map<String, Object> result;
        try {
            if (timeout == null) {
                result = future.get();
            } else {
                long millis = Math.max(0L, Math.round(timeout * 1000.0d));
                result = future.get(millis, TimeUnit.MILLISECONDS);
            }
        } catch (TimeoutException exception) {
            future.cancel(false);
            result = resultMap("error", "completion_timeout");
        } catch (CancellationException exception) {
            result = resultMap("error", "cancelled");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            result = resultMap("error", "interrupted");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            result = resultMap("error", cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage());
        }

        lastResult = normalizeCompletionResult(result);
        return getLastResult();
    }

    @Override
    public Map<String, Object> handleInput(EventHandlerInput inputs) {
        Event event = inputs == null ? null : inputs.getEvent();
        Map<String, Object> metadata = metadataOf(event);
        int currentRound = intValue(metadata.get("_handler_round_id"), roundId);

        if (deepAgent == null || getLoopCoordinatorFromAgent() == null) {
            resolveFuture(resultMap("error", "no LoopCoordinator"), currentRound);
            return resultMap("status", "failed");
        }

        String taskId = stringValue(metadata.get("task_id"));
        boolean followUp = booleanValue(metadata.get("is_follow_up"));
        if (taskId == null || taskId.isBlank()) {
            taskId = UUID.randomUUID().toString().replace("-", "");
        }

        String sessionId = inputs == null || inputs.getSession() == null
                ? "default"
                : stringOrDefault(inputs.getSession().getSessionId(), "default");
        Map<String, Object> taskMetadata = new LinkedHashMap<>();
        taskMetadata.put("_handler_round_id", currentRound);
        taskMetadata.put("run_kind", metadata.get("run_kind"));
        taskMetadata.put("run_context", metadata.get("run_context"));
        taskMetadata.put("is_follow_up", followUp);

        try {
            Task task = new Task(sessionId, taskId, TaskLoopEventExecutor.DEEP_TASK_TYPE);
            task.setDescription(extractQuery(event));
            task.setStatus(TaskStatus.SUBMITTED);
            task.setMetadata(taskMetadata);
            if (event instanceof InputEvent) {
                task.setInputs(List.of(event));
            }
            if (taskManager == null) {
                resolveFuture(resultMap("error", "task_manager is None"), currentRound);
                return resultMap("status", "failed");
            }
            taskManager.addTask(task);
        } catch (RuntimeException exception) {
            resolveFuture(resultMap("error", exception.getMessage()), currentRound);
            Map<String, Object> result = resultMap("status", "failed");
            result.put("error", exception.getMessage());
            return result;
        }

        Map<String, Object> result = resultMap("status", "submitted");
        result.put("task_id", taskId);
        return result;
    }

    @Override
    public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
        String message = "";
        Event event = inputs == null ? null : inputs.getEvent();
        if (event instanceof TaskInteractionEvent interactionEvent && !interactionEvent.getInteraction().isEmpty()) {
            DataFrame frame = interactionEvent.getInteraction().get(0);
            if (!isStructuredInterrupt(frame)) {
                message = frameText(frame);
            }
        }
        if (!message.isBlank() && interactionQueues != null) {
            interactionQueues.pushSteer(message);
        }
        Map<String, Object> result = resultMap("status", "steer_injected");
        result.put("msg", message);
        return result;
    }

    @Override
    public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
        Event event = inputs == null ? null : inputs.getEvent();
        Map<String, Object> metadata = metadataOf(event);
        String taskId = stringValue(metadata.get("task_id"));
        int currentRound = intValue(metadata.get("_handler_round_id"), roundId);
        Map<String, Object> payload = new LinkedHashMap<>();
        if (event instanceof TaskCompletionEvent completionEvent) {
            payload = extractCompletionResult(completionEvent.getTaskResult());
        }
        resolveFuture(payload, currentRound);

        Map<String, Object> result = resultMap("status", "completed");
        result.put("task_id", taskId);
        return result;
    }

    @Override
    public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
        Event event = inputs == null ? null : inputs.getEvent();
        Map<String, Object> metadata = metadataOf(event);
        String taskId = stringValue(metadata.get("task_id"));
        int currentRound = intValue(metadata.get("_handler_round_id"), roundId);
        String errorMessage = "unknown";
        if (event instanceof TaskFailedEvent failedEvent && failedEvent.getErrorMessage() != null) {
            errorMessage = failedEvent.getErrorMessage();
        }
        resolveFuture(resultMap("error", errorMessage), currentRound);

        Map<String, Object> result = resultMap("status", "failed");
        result.put("task_id", taskId);
        result.put("error", errorMessage);
        return result;
    }

    @Override
    public Map<String, Object> handleFollowUp(EventHandlerInput inputs) {
        String message = "";
        Event event = inputs == null ? null : inputs.getEvent();
        if (event instanceof FollowUpEvent followUpEvent) {
            for (DataFrame frame : followUpEvent.getInputData()) {
                message = frameText(frame);
                if (!message.isBlank()) {
                    break;
                }
            }
        }
        if (!message.isBlank() && interactionQueues != null) {
            interactionQueues.pushFollowUp(message);
        }
        Map<String, Object> result = resultMap("status", "follow_up_queued");
        result.put("msg", message);
        return result;
    }

    public Map<String, Object> completeSessionSpawn(
            String taskId,
            Map<String, Object> inputs,
            boolean error
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "session_spawn");
        result.put("task_id", taskId);
        result.put("error", error);
        result.put("inputs", inputs == null ? Map.of() : new LinkedHashMap<>(inputs));
        interactionQueues.output().add(result);
        lastResult = result;
        return result;
    }

    @Override
    public void onAbort() {
        resolveFuture(resultMap("error", "aborted"), roundId);
    }

    void resolveFuture(Map<String, Object> result, int targetRoundId) {
        CompletableFuture<Map<String, Object>> future = currentFuture;
        if (targetRoundId != roundId || future == null || future.isDone()) {
            return;
        }
        future.complete(result == null ? new LinkedHashMap<>() : new LinkedHashMap<>(result));
    }

    private static Map<String, Object> metadataOf(Event event) {
        return event == null || event.getMetadata() == null ? Map.of() : event.getMetadata();
    }

    private static Map<String, Object> extractCompletionResult(List<DataFrame> taskResult) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (taskResult == null) {
            return result;
        }
        for (DataFrame frame : taskResult) {
            if (frame instanceof DataFrame.JsonDataFrame jsonDataFrame && jsonDataFrame.data() != null) {
                return new LinkedHashMap<>(jsonDataFrame.data());
            }
            String text = frameText(frame);
            if (!text.isBlank()) {
                result.put("output", text);
            }
        }
        return result;
    }

    private static String extractQuery(Event event) {
        if (!(event instanceof InputEvent inputEvent)) {
            return "";
        }
        for (DataFrame frame : inputEvent.getInputData()) {
            if (frame instanceof DataFrame.TextDataFrame textDataFrame && textDataFrame.text() != null
                    && !textDataFrame.text().isBlank()) {
                return textDataFrame.text();
            }
            if (frame instanceof DataFrame.JsonDataFrame jsonDataFrame && jsonDataFrame.data() != null) {
                Object query = jsonDataFrame.data().get("query");
                return query == null ? String.valueOf(jsonDataFrame.data()) : String.valueOf(query);
            }
        }
        return "";
    }

    private static boolean isStructuredInterrupt(DataFrame frame) {
        if (!(frame instanceof DataFrame.JsonDataFrame jsonDataFrame) || jsonDataFrame.data() == null) {
            return false;
        }
        Map<String, Object> data = jsonDataFrame.data();
        return Constant.INTERACTION.equals(String.valueOf(data.get("type")))
                || "interrupt".equals(String.valueOf(data.get("result_type")));
    }

    private static String frameText(DataFrame frame) {
        if (frame instanceof DataFrame.TextDataFrame textDataFrame && textDataFrame.text() != null) {
            return textDataFrame.text();
        }
        if (frame instanceof DataFrame.JsonDataFrame jsonDataFrame && jsonDataFrame.data() != null) {
            return String.valueOf(jsonDataFrame.data());
        }
        return frame == null ? "" : String.valueOf(frame);
    }

    private static Map<String, Object> normalizeCompletionResult(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return resultMap("status", "completed");
        }
        return normalizeMap(result);
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static Map<String, Object> resultMap(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String stringOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * Retrieve the LoopCoordinator from the deepAgent via reflection.
     * Supports both {@code com.openjiuwen.harness.DeepAgent} (loopCoordinator())
     * and {@code com.openjiuwen.harness.deep_agent.DeepAgent} (getLoopCoordinator()).
     *
     * @return the LoopCoordinator, or null if unavailable
     */
    private Object getLoopCoordinatorFromAgent() {
        if (deepAgent == null) {
            return null;
        }
        try {
            // Try getLoopCoordinator() first (Lombok @Getter style)
            Method getter = deepAgent.getClass().getMethod("getLoopCoordinator");
            return getter.invoke(deepAgent);
        } catch (NoSuchMethodException ignored) {
            // fall through
        } catch (Exception e) {
            return null;
        }
        try {
            // Try loopCoordinator() (hand-written accessor style)
            Method accessor = deepAgent.getClass().getMethod("loopCoordinator");
            return accessor.invoke(deepAgent);
        } catch (Exception ignored) {
            return null;
        }
    }
}
