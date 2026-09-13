/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Public class TaskLoopEventExecutor used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class TaskLoopEventExecutor {
    /**
     * DEEP_TASK_TYPE.
     * 
     * @since 0.1.7
     */
    public static final String DEEP_TASK_TYPE = "deep_agent_task";

    private final Function<Map<String, Object>, Map<String, Object>> taskInvoker;
    private final Consumer<TaskIterationContext> afterTaskIteration;

    /**
     * TaskLoopEventExecutor.
     * 
     * @param taskInvoker taskInvoker
     * @since 0.1.7
     */
    public TaskLoopEventExecutor(Function<Map<String, Object>, Map<String, Object>> taskInvoker) {
        this(taskInvoker, null);
    }

    /**
     * TaskLoopEventExecutor.
     * 
     * @param taskInvoker taskInvoker
     * @param afterTaskIteration afterTaskIteration
     * @since 0.1.7
     */
    public TaskLoopEventExecutor(Function<Map<String, Object>, Map<String, Object>> taskInvoker,
            Consumer<TaskIterationContext> afterTaskIteration) {
        this.taskInvoker = taskInvoker;
        this.afterTaskIteration = afterTaskIteration;
    }

    /**
     * execute.
     * 
     * @param taskId taskId
     * @param query query
     * @param metadata metadata
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> execute(String taskId, String query, Map<String, Object> metadata) {
        Map<String, Object> effective = new LinkedHashMap<>();
        effective.put("query", query);
        effective.put("task_id", taskId);
        if (metadata != null) {
            if (metadata.get("run_kind") != null) {
                effective.put("run_kind", metadata.get("run_kind"));
            }
            if (metadata.get("run_context") != null) {
                effective.put("run_context", metadata.get("run_context"));
            }
            if (metadata.get("is_follow_up") != null) {
                effective.put("is_follow_up", metadata.get("is_follow_up"));
            }
        }
        try {
            Map<String, Object> result = taskInvoker == null ? Map.of("output", query) : taskInvoker.apply(effective);
            fireAfterTaskIteration(taskId, effective, result == null ? Map.of() : result, null);
            return Map.of("type", "TASK_COMPLETION", "task_id", taskId, "task_type", DEEP_TASK_TYPE, "data",
                    result == null ? Map.of() : result);
        } catch (RuntimeException ex) {
            fireAfterTaskIteration(taskId, effective, Map.of("error", errorMessage(ex)), ex);
            return Map.of("type", "TASK_FAILED", "task_id", taskId, "task_type", DEEP_TASK_TYPE, "error",
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    /**
     * fireAfterTaskIteration.
     * 
     * @param taskId taskId
     * @param inputs inputs
     * @param result result
     * @param exception exception
     * @since 0.1.7
     */
    private void fireAfterTaskIteration(String taskId, Map<String, Object> inputs, Map<String, Object> result,
            RuntimeException exception) {
        if (afterTaskIteration == null) {
            return;
        }
        afterTaskIteration.accept(TaskIterationContext.builder().inputs(new LinkedHashMap<>(inputs))
                .result(new LinkedHashMap<>(result)).usageMetadata(TaskIterationContext.usageMetadataFrom(result))
                .exception(exception).isFollowUp(Boolean.TRUE.equals(inputs.get("is_follow_up"))).build());
    }

    /**
     * canPause.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean canPause() {
        return false;
    }

    /**
     * canCancel.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean canCancel() {
        return true;
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
}
