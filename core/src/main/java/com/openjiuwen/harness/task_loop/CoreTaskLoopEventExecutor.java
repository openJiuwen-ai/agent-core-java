/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.openjiuwen.core.controller.modules.TaskExecutor;
import com.openjiuwen.core.controller.modules.TaskExecutorDependencies;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.schema.task.TaskPlan;
import com.openjiuwen.harness.schema.task.TodoItem;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Core controller TaskExecutor bridge for DeepAgent task-loop tasks.
 * 
 * @since 0.1.7
 */
public class CoreTaskLoopEventExecutor extends TaskExecutor {
    private final DeepAgent deepAgent;
    private final BiFunction<Map<String, Object>, AgentSessionApi, Map<String, Object>> taskInvoker;

    /**
     * CoreTaskLoopEventExecutor.
     * 
     * @param dependencies dependencies
     * @param deepAgent deepAgent
     * @since 0.1.7
     */
    public CoreTaskLoopEventExecutor(TaskExecutorDependencies dependencies, DeepAgent deepAgent) {
        this(dependencies, deepAgent, (BiFunction<Map<String, Object>, AgentSessionApi, Map<String, Object>>) null);
    }

    /**
     * CoreTaskLoopEventExecutor.
     * 
     * @param dependencies dependencies
     * @param taskInvoker taskInvoker
     * @since 0.1.7
     */
    public CoreTaskLoopEventExecutor(TaskExecutorDependencies dependencies,
            Function<Map<String, Object>, Map<String, Object>> taskInvoker) {
        this(dependencies, null, taskInvoker);
    }

    /**
     * CoreTaskLoopEventExecutor.
     * 
     * @param dependencies dependencies
     * @param deepAgent deepAgent
     * @param taskInvoker taskInvoker
     * @since 0.1.7
     */
    public CoreTaskLoopEventExecutor(TaskExecutorDependencies dependencies, DeepAgent deepAgent,
            Function<Map<String, Object>, Map<String, Object>> taskInvoker) {
        this(dependencies, deepAgent,
                taskInvoker == null ? null : (effective, session) -> taskInvoker.apply(effective));
    }

    /**
     * CoreTaskLoopEventExecutor.
     * 
     * @param dependencies dependencies
     * @param deepAgent deepAgent
     * @param taskInvoker taskInvoker
     * @since 0.1.7
     */
    public CoreTaskLoopEventExecutor(TaskExecutorDependencies dependencies, DeepAgent deepAgent,
            BiFunction<Map<String, Object>, AgentSessionApi, Map<String, Object>> taskInvoker) {
        super(dependencies);
        this.deepAgent = deepAgent;
        this.taskInvoker = taskInvoker;
    }

    /**
     * executeAbility.
     * 
     * @param taskId taskId
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
        return executeOnce(taskId, session).iterator();
    }

    /**
     * canPause.
     * 
     * @param taskId taskId
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public PauseCheckResult canPause(String taskId, AgentSessionApi session) {
        return new PauseCheckResult(false, "DeepAgent task-loop tasks cannot be paused");
    }

    /**
     * pause.
     * 
     * @param taskId taskId
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean pause(String taskId, AgentSessionApi session) {
        return false;
    }

    /**
     * canCancel.
     * 
     * @param taskId taskId
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public CancelCheckResult canCancel(String taskId, AgentSessionApi session) {
        return new CancelCheckResult(true, "");
    }

    /**
     * cancel.
     * 
     * @param taskId taskId
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean cancel(String taskId, AgentSessionApi session) {
        if (deepAgent != null) {
            deepAgent.requestAbort();
        }
        return true;
    }

    /**
     * executeOnce.
     * 
     * @param taskId taskId
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private List<ControllerOutputChunk> executeOnce(String taskId, AgentSessionApi session) {
        Task task = resolveTask(taskId);
        Map<String, Object> effective = buildEffectiveInputs(taskId, task, session);
        DeepAgentState state = loadPlanState(session);
        if (getPlanTask(state, taskId) != null) {
            state.getTaskPlan().markInProgress(taskId);
            savePlanState(session, state);
        }
        try {
            Map<String, Object> result = invokeTask(effective, session);
            if (!isInterruptResult(result) && getPlanTask(loadPlanState(session), taskId) != null) {
                DeepAgentState completedState = loadPlanState(session);
                String summary = String.valueOf(result == null ? "" : result.getOrDefault("output", ""));
                if (summary.length() > 200) {
                    summary = summary.substring(0, 200);
                }
                completedState.getTaskPlan().markCompleted(taskId, summary);
                savePlanState(session, completedState);
            }
            fireAfterTaskIteration(task, session, effective, result, null);
            // Align with Python: interrupt still completes the round via TASK_COMPLETION so the
            // outer loop's wait_completion Future resolves (TASK_INTERACTION is for steer only).
            List<ControllerOutputChunk> chunks = new java.util.ArrayList<>(processingChunks(result, taskId));
            ControllerOutputPayload payload = new ControllerOutputPayload(EventType.TASK_COMPLETION.getValue(),
                    List.of(new DataFrame.JsonDataFrame(result == null ? Map.of() : result)),
                    Map.of("task_id", taskId));
            chunks.add(new ControllerOutputChunk(chunks.size(), payload, true));
            return chunks;
        } catch (RuntimeException ex) {
            DeepAgentState failedState = loadPlanState(session);
            if (getPlanTask(failedState, taskId) != null) {
                failedState.getTaskPlan().markCancelled(taskId, errorMessage(ex));
                savePlanState(session, failedState);
            }
            fireAfterTaskIteration(task, session, effective, Map.of("error", errorMessage(ex)), ex);
            ControllerOutputPayload payload = new ControllerOutputPayload(EventType.TASK_FAILED.getValue(),
                    List.of(new DataFrame.TextDataFrame(errorMessage(ex))), Map.of("task_id", taskId));
            return List.of(new ControllerOutputChunk(0, payload, true));
        }
    }

    private DeepAgentState loadPlanState(AgentSessionApi session) {
        if (deepAgent == null || session == null) {
            return null;
        }
        return deepAgent.loadState(session);
    }

    private void savePlanState(AgentSessionApi session, DeepAgentState state) {
        if (deepAgent == null || session == null || state == null) {
            return;
        }
        deepAgent.saveState(session, state);
    }

    private static TodoItem getPlanTask(DeepAgentState state, String taskId) {
        if (state == null || taskId == null || taskId.isBlank()) {
            return null;
        }
        TaskPlan plan = state.getTaskPlan();
        return plan == null ? null : plan.getTask(taskId);
    }

    /**
     * fireAfterTaskIteration.
     * 
     * @param task task
     * @param session session
     * @param effective effective
     * @param result result
     * @param exception exception
     * @since 0.1.7
     */
    private void fireAfterTaskIteration(Task task, AgentSessionApi session, Map<String, Object> effective,
            Map<String, Object> result, RuntimeException exception) {
        if (deepAgent == null) {
            return;
        }
        Map<String, Object> metadata = task.getMetadata() == null ? Map.of() : task.getMetadata();
        deepAgent.fireAfterTaskIteration(TaskIterationContext.builder().agent(deepAgent).task(task).session(session)
                .round(intValue(metadata.get("_handler_round_id"), 0))
                .isFollowUp(Boolean.TRUE.equals(effective.get("is_follow_up"))).inputs(new LinkedHashMap<>(effective))
                .result(result == null ? new LinkedHashMap<>() : new LinkedHashMap<>(result))
                .usageMetadata(TaskIterationContext.usageMetadataFrom(result)).exception(exception).build());
    }

    /**
     * resolveTask.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    private Task resolveTask(String taskId) {
        List<Task> tasks = taskManager.getTask(TaskFilter.byTaskId(taskId));
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        return tasks.get(0);
    }

    /**
     * buildEffectiveInputs.
     * 
     * @param taskId taskId
     * @param task task
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> buildEffectiveInputs(String taskId, Task task, AgentSessionApi session) {
        Map<String, Object> effective = new LinkedHashMap<>();
        effective.put("query", resolveQuery(taskId, task));
        effective.put("task_id", taskId);
        effective.put("conversation_id", session != null ? session.getSessionId() : task.getSessionId());
        Map<String, Object> metadata = task.getMetadata() == null ? Map.of() : task.getMetadata();
        copyIfPresent(metadata, effective, "run_kind");
        copyIfPresent(metadata, effective, "run_context");
        copyIfPresent(metadata, effective, "is_follow_up");
        copyIfPresent(metadata, effective, "_handler_round_id");
        copyIfPresent(metadata, effective, "collect_inner_stream");
        copyIfPresent(metadata, effective, "loop_queues");
        LoopQueues queues = null;
        if (metadata.get("loop_queues") instanceof LoopQueues typed) {
            queues = typed;
        } else if (deepAgent != null && deepAgent.loopController() != null && session != null) {
            queues = deepAgent.loopController().getInteractionQueues(session.getSessionId());
        }
        if (queues != null) {
            // Live queue reference so steer during a blocked invoke is visible to the next round.
            effective.put("_steering_queue", queues.steering());
        }
        return effective;
    }

    /**
     * resolveQuery.
     * 
     * @param taskId taskId
     * @param task task
     * @return the result
     * @since 0.1.7
     */
    private Object resolveQuery(String taskId, Task task) {
        if (task != null && task.getInputs() != null) {
            for (Object input : task.getInputs()) {
                if (input instanceof com.openjiuwen.core.controller.schema.InputEvent event) {
                    for (DataFrame frame : event.getInputData()) {
                        if (frame instanceof DataFrame.TextDataFrame textDataFrame && textDataFrame.text() != null) {
                            return textDataFrame.text();
                        }
                        if (frame instanceof DataFrame.JsonDataFrame jsonDataFrame && jsonDataFrame.data() != null) {
                            Object query = jsonDataFrame.data().get("query");
                            if (query != null) {
                                return query;
                            }
                            return new LinkedHashMap<>(jsonDataFrame.data());
                        }
                    }
                }
            }
        }
        String description = task.getDescription();
        return description == null || description.isBlank() ? taskId : description;
    }

    /**
     * invokeTask.
     * 
     * @param effective effective
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> invokeTask(Map<String, Object> effective, AgentSessionApi session) {
        if (taskInvoker != null) {
            return taskInvoker.apply(effective, session);
        }
        if (deepAgent != null) {
            return deepAgent.invoke(effective, session);
        }
        return Map.of("output", effective.getOrDefault("query", ""));
    }

    /**
     * processingChunks.
     * 
     * @param result result
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    /**
     * Convert inner stream chunks into controller output chunks.
     * <p>
     * Parallel {@code __interaction__} members are merged into a single
     * {@code TASK_INTERACTION} chunk ({@code data:[N]}) so TaskScheduler's
     * first-chunk break still publishes all siblings (issue #66).
     */
    static List<ControllerOutputChunk> processingChunks(Map<String, Object> result, String taskId) {
        if (result == null) {
            return List.of();
        }
        Object raw = firstPresent(result, new String[]{"stream_chunks", "streamChunks", "chunks", "inner_stream"});
        if (!(raw instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<ControllerOutputChunk> chunks = new java.util.ArrayList<>();
        List<DataFrame> interactionFrames = new java.util.ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof com.openjiuwen.core.session.stream.OutputSchema outputSchema
                    && "__interaction__".equals(outputSchema.getType())) {
                interactionFrames.add(new DataFrame.JsonDataFrame(
                        Map.of("type", outputSchema.getType(), "payload", outputSchema.getPayload())));
                continue;
            }
            ControllerOutputChunk chunk = toProcessingChunk(item, chunks.size(), taskId);
            if (chunk != null) {
                chunks.add(chunk);
            }
        }
        if (!interactionFrames.isEmpty()) {
            chunks.add(new ControllerOutputChunk(chunks.size(),
                    new ControllerOutputPayload(EventType.TASK_INTERACTION.getValue(), interactionFrames,
                            Map.of("task_id", taskId, "stream_kind", "inner_agent")),
                    false));
        }
        return chunks;
    }

    /**
     * firstPresent.
     * 
     * @param result result
     * @param keys keys
     * @return the result
     * @since 0.1.7
     */
    private static Object firstPresent(Map<String, Object> result, String[] keys) {
        for (String key : keys) {
            if (result.containsKey(key)) {
                return result.get(key);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    /**
     * toProcessingChunk.
     * 
     * @param item item
     * @param index index
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    private static ControllerOutputChunk toProcessingChunk(Object item, int index, String taskId) {
        if (item instanceof ControllerOutputChunk chunk) {
            chunk.setIndex(index);
            chunk.setLastChunk(false);
            return chunk;
        }
        Map<String, Object> metadata = Map.of("task_id", taskId, "stream_kind", "inner_agent");
        List<DataFrame> data;
        if (item instanceof DataFrame frame) {
            data = List.of(frame);
        } else if (item instanceof Map<?, ?> map) {
            data = List.of(new DataFrame.JsonDataFrame(castMap(map)));
        } else if (item instanceof com.openjiuwen.core.session.stream.OutputSchema outputSchema) {
            // Processing frame (interactions are batched in processingChunks).
            Object payload = outputSchema.getPayload();
            if (payload instanceof Map<?, ?> map) {
                data = List.of(new DataFrame.JsonDataFrame(castMap(map)));
            } else if (payload != null) {
                data = List.of(new DataFrame.TextDataFrame(String.valueOf(payload)));
            } else {
                data = List.of(new DataFrame.JsonDataFrame(Map.of("type", outputSchema.getType())));
            }
        } else if (item != null) {
            data = List.of(new DataFrame.TextDataFrame(String.valueOf(item)));
        } else {
            return null;
        }
        return new ControllerOutputChunk(index,
                new ControllerOutputPayload(ControllerOutputPayload.TASK_PROCESSING, data, metadata), false);
    }

    /**
     * isInterruptResult.
     * 
     * @param result result
     * @return the result
     * @since 0.1.7
     */
    private static boolean isInterruptResult(Map<String, Object> result) {
        return result != null && "interrupt".equals(String.valueOf(result.get("result_type")));
    }

    /**
     * copyIfPresent.
     * 
     * @param source source
     * @param target target
     * @param key key
     * @since 0.1.7
     */
    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    /**
     * errorMessage.
     * 
     * @param ex ex
     * @return the result
     * @since 0.1.7
     */
    private static String errorMessage(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    /**
     * intValue.
     * 
     * @param value value
     * @param fallback fallback
     * @return the result
     * @since 0.1.7
     */
    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null && !String.valueOf(value).isBlank()) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * castMap.
     * 
     * @param source source
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
