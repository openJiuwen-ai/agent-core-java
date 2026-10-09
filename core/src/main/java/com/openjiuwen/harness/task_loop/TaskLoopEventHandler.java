/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

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
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.schema.task.TaskPlan;
import com.openjiuwen.harness.schema.task.TodoItem;

import java.lang.reflect.Method;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Event handler used by the DeepAgent task-loop controller.
 *
 * <p>Mirrors Python's {@code TaskLoopEventHandler} in
 * {@code openjiuwen/harness/task_loop/task_loop_event_handler.py}.</p>
 */
public class TaskLoopEventHandler extends EventHandler {

    private final Object deepAgent;
    private LoopQueues interactionQueues = new LoopQueues();

    /**
     * Round state must be isolated per session: a single shared future/round pair let
     * concurrent sessions cancel each other's in-flight rounds and drop resolved results
     * (round id mismatch), surfacing as wrong round results and "Task not found in
     * TaskManager" under concurrent load.
     */
    private final ConcurrentMap<Integer, RoundState> roundStates = new ConcurrentHashMap<>();

    /** Monotonic round id generator shared by every session (ids stay globally unique). */
    private final AtomicInteger roundCounter = new AtomicInteger();
    private Map<String, Object> lastResult;
    private Object sessionToolkit;
    private final ThreadLocal<Integer> currentThreadRoundId = new ThreadLocal<>();

    /**
     * Create an event handler with a deep agent reference.
     *
     * <p>Accepts either {@code com.openjiuwen.harness.deep_agent.DeepAgent} or
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

    /** Per-session (per-round) round state replacing the former shared fields. */
    private static final class RoundState {
        final String sessionId;
        volatile CompletableFuture<Map<String, Object>> future;

        RoundState(String sessionId) {
            this.sessionId = sessionId == null || sessionId.isBlank() ? "default" : sessionId;
        }
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
        return prepareRound(null, false);
    }

    /**
     * Prepare a new round bound to the given session.
     *
     * <p>Round ids are globally unique and each round keeps its own future, so
     * concurrent sessions never observe each other's futures.</p>
     *
     * @param sessionId  the session id (null keeps legacy default-session behavior)
     * @param isFollowUp whether this is a follow-up round
     * @return the new round id
     */
    public synchronized int prepareRound(String sessionId, boolean isFollowUp) {
        String normalized = sessionId == null || sessionId.isBlank() ? "default" : sessionId;
        // Port of the former shared-field cancel: only stale in-flight rounds of the
        // calling session are cancelled — never another session's round.
        roundStates.forEach((id, existing) -> {
            if (normalized.equals(existing.sessionId) && existing.future != null && !existing.future.isDone()) {
                existing.future.cancel(false);
            }
        });
        roundStates.values().removeIf(existing -> normalized.equals(existing.sessionId));
        RoundState state = new RoundState(normalized);
        state.future = new CompletableFuture<>();
        int newRoundId = roundCounter.incrementAndGet();
        roundStates.put(newRoundId, state);
        // The prepare→wait pair happens on one session thread; binding the thread to
        // the new round keeps waitCompletion/resolveFuture correlated for this session.
        currentThreadRoundId.set(newRoundId);
        return newRoundId;
    }

    @Override
    public Map<String, Object> waitCompletion(Double timeout) {
        return waitCompletion(timeout, null);
    }

    /**
     * Wait for completion of the round owned by the given session.
     *
     * <p>Round correlation precedence:
     * <ol>
     *   <li>thread round id — a session thread that called {@link #prepareRound}
     *       on this handler (task-loop path);</li>
     *   <li>session round binding — the latest round registered for the session
     *       (inner executor waiting on behalf of the session thread);</li>
     *   <li>legacy fallback — the most recent round regardless of session, only
     *       when no session id is given.</li>
     * </ol></p>
     *
     * @param timeout   max seconds to wait, or null
     * @param sessionId the session id, or null for the legacy fallback
     * @return the round result map
     */
    public Map<String, Object> waitCompletion(Double timeout, String sessionId) {
        RoundState state = stateForWait(sessionId);
        if (state == null) {
            lastResult = resultMap("error", "no active round");
            return getLastResult();
        }
        CompletableFuture<Map<String, Object>> future = state.future;

        Map<String, Object> result;
        try {
            if (timeout == null) {
                result = future.get();
            } else {
                long millis = Math.max(0L, Math.round(timeout * 1000.0d));
                result = future.get(millis, TimeUnit.MILLISECONDS);
            }
        } catch (TimeoutException exception) {
            // Leave the round registered and its future uncancelled so the caller's
            // retry loop keeps waiting on the same round; a late completion still
            // lands on this future instead of being dropped.
            lastResult = resultMap("error", "completion_timeout");
            return getLastResult();
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
        cleanupRound(state);
        return getLastResult();
    }

    /**
     * Resolve the round to wait for, following the correlation precedence of
     * {@link #waitCompletion(Double, String)}.
     *
     * @param sessionId the session id, or null for the legacy fallback
     * @return the resolved round state, or null if no active round is found
     */
    private RoundState stateForWait(String sessionId) {
        Integer threadRound = currentThreadRoundId.get();
        if (threadRound != null) {
            RoundState state = roundStates.get(threadRound);
            if (state != null) {
                return state;
            }
        }
        if (sessionId != null) {
            RoundState state = roundStates.values().stream()
                    .filter(candidate -> sessionId.equals(candidate.sessionId))
                    .reduce((first, second) -> second)
                    .orElse(null);
            if (state != null) {
                return state;
            }
        }
        return roundStates.values().stream()
                .reduce((first, second) -> second)
                .orElse(null);
    }

    @Override
    public Map<String, Object> handleInput(EventHandlerInput inputs) {
        Event event = inputs == null ? null : inputs.getEvent();
        Map<String, Object> metadata = metadataOf(event);
        String inputSessionId = inputs == null || inputs.getSession() == null
                ? "default"
                : stringOrDefault(inputs.getSession().getSessionId(), "default");
        int currentRound = intValue(metadata.get("_handler_round_id"),
                fallbackRoundId(inputSessionId));

        if (deepAgent == null || getLoopCoordinatorFromAgent() == null) {
            resolveFuture(resultMap("error", "no LoopCoordinator"), currentRound);
            return resultMap("status", "failed");
        }

        String taskId = stringValue(metadata.get("task_id"));
        boolean followUp = booleanValue(metadata.get("is_follow_up"));
        // Resolve task_id from TaskPlan when available (skip for follow_up — use random UUID).
        if ((taskId == null || taskId.isBlank()) && !followUp && inputs != null && inputs.getSession() != null) {
            TodoItem nextTask = nextPlanTask(inputs.getSession());
            if (nextTask != null && nextTask.getId() != null && !nextTask.getId().isBlank()) {
                taskId = nextTask.getId();
            }
        }
        if (taskId == null || taskId.isBlank()) {
            taskId = UUID.randomUUID().toString().replace("-", "");
        }

        Map<String, Object> taskMetadata = new LinkedHashMap<>();
        taskMetadata.put("_handler_round_id", currentRound);
        taskMetadata.put("run_kind", metadata.get("run_kind"));
        taskMetadata.put("run_context", metadata.get("run_context"));
        taskMetadata.put("is_follow_up", followUp);
        if (metadata.containsKey("collect_inner_stream")) {
            taskMetadata.put("collect_inner_stream", metadata.get("collect_inner_stream"));
        }
        if (metadata.containsKey("loop_queues")) {
            taskMetadata.put("loop_queues", metadata.get("loop_queues"));
        }
        if (metadata.containsKey("_invoke_extras")) {
            taskMetadata.put("_invoke_extras", metadata.get("_invoke_extras"));
        }
        copyModelSelectionKeys(metadata, taskMetadata);

        try {
            String sessionId = inputSessionId;
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
            // Structured interrupt control info (type=__interaction__ / result_type=interrupt)
            // must stay in the interrupt state and never enter the steering queue as text.
            if (!isStructuredInterruptFrame(frame)) {
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
        int currentRound = intValue(metadata.get("_handler_round_id"), fallbackRoundId("default"));
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
        int currentRound = intValue(metadata.get("_handler_round_id"), fallbackRoundId("default"));
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
        Integer threadRound = currentThreadRoundId.get();
        if (threadRound != null) {
            resolveFuture(resultMap("error", "aborted"), threadRound);
            return;
        }
        roundStates.values().forEach(state ->
                resolveFuture(resultMap("error", "aborted"), state.future));
    }

    void resolveFuture(Map<String, Object> result, int targetRoundId) {
        RoundState state = roundStates.get(targetRoundId);
        if (state == null) {
            return;
        }
        resolveFuture(result, state.future);
    }

    private void resolveFuture(Map<String, Object> result, CompletableFuture<Map<String, Object>> future) {
        if (future == null || future.isDone()) {
            return;
        }
        future.complete(result == null ? new LinkedHashMap<>() : new LinkedHashMap<>(result));
    }

    /**
     * Drop a finished round's state so concurrent rounds are never matched to a
     * stale future, and stale entries cannot accumulate across sessions.
     *
     * @param state the round state to clean up, or null (no-op)
     */
    private void cleanupRound(RoundState state) {
        if (state == null) {
            return;
        }
        roundStates.values().removeIf(candidate -> candidate == state);
        Integer threadRound = currentThreadRoundId.get();
        if (threadRound != null && roundStates.get(threadRound) == null) {
            currentThreadRoundId.remove();
        }
    }

    /**
     * Drop every round registered for the given session (used when a session's
     * task-loop runtime is stopped).
     *
     * @param sessionId the session id
     */
    public void clearSessionRounds(String sessionId) {
        if (sessionId == null) {
            return;
        }
        String normalized = sessionId.isBlank() ? "default" : sessionId;
        roundStates.values().removeIf(state -> normalized.equals(state.sessionId));
    }

    /**
     * Drop every round state (used when the owning agent is destroyed).
     */
    public void clearAllRounds() {
        roundStates.clear();
    }

    private static Map<String, Object> metadataOf(Event event) {
        return event == null || event.getMetadata() == null ? Map.of() : event.getMetadata();
    }

    /**
     * Fallback round id when an event carries no explicit {@code _handler_round_id}.
     *
     * <p>Precedence: the calling thread's round (task-loop path), then the latest
     * round registered for the session, then the globally latest round.</p>
     *
     * @param sessionId the session id, or null for the global fallback
     * @return the resolved round id, or 0 if no rounds exist
     */
    private int fallbackRoundId(String sessionId) {
        Integer threadRound = currentThreadRoundId.get();
        if (threadRound != null && roundStates.containsKey(threadRound)) {
            return threadRound;
        }
        if (sessionId != null) {
            Integer sessionRound = roundStates.entrySet().stream()
                    .filter(entry -> sessionId.equals(entry.getValue().sessionId))
                    .map(Map.Entry::getKey)
                    .reduce((first, second) -> second)
                    .orElse(null);
            if (sessionRound != null) {
                return sessionRound;
            }
        }
        return roundStates.keySet().stream()
                .reduce(Math::max)
                .orElse(0);
    }

    private static void copyModelSelectionKeys(Map<String, Object> source, Map<String, Object> target) {
        if (source == null || target == null) {
            return;
        }
        copyStringIfPresent(source, target, "model_id");
        copyStringIfPresent(source, target, "target_model_id");
        copyStringIfPresent(source, target, "dynamic_model_id");
    }

    private static void copyStringIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value instanceof String text && !text.isBlank()) {
            target.put(key, text);
        }
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

    private static String frameText(DataFrame frame) {
        if (frame instanceof DataFrame.TextDataFrame textDataFrame && textDataFrame.text() != null) {
            return textDataFrame.text();
        }
        if (frame instanceof DataFrame.JsonDataFrame jsonDataFrame && jsonDataFrame.data() != null) {
            return String.valueOf(jsonDataFrame.data());
        }
        return frame == null ? "" : String.valueOf(frame);
    }

    private static boolean isStructuredInterruptFrame(DataFrame frame) {
        if (!(frame instanceof DataFrame.JsonDataFrame jsonDataFrame) || jsonDataFrame.data() == null) {
            return false;
        }
        return "__interaction__".equals(String.valueOf(jsonDataFrame.data().get("type")))
                || "interrupt".equals(String.valueOf(jsonDataFrame.data().get("result_type")));
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

    private TodoItem nextPlanTask(AgentSessionApi session) {
        if (!(deepAgent instanceof DeepAgent agent) || session == null) {
            return null;
        }
        DeepAgentState state = agent.loadState(session);
        TaskPlan plan = state == null ? null : state.getTaskPlan();
        return plan == null ? null : plan.getNextTask();
    }

    /**
     * Retrieve the LoopCoordinator from the deepAgent via reflection.
     * Supports both {@code com.openjiuwen.harness.deep_agent.DeepAgent} (loopCoordinator())
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
