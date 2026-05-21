/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * TaskExecutor that delegates to the inner agent execution.
 *
 * <p>Wraps inner agent execution as a TaskExecutor
 * so it can be driven by the core TaskScheduler.
 *
 * <p>Mirrors Python's {@code TaskLoopEventExecutor} in
 * {@code openjiuwen.harness.task_loop.task_loop_event_executor}.
 */
public class TaskLoopEventExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(TaskLoopEventExecutor.class);

    /** Task type for deep agent tasks. */
    public static final String DEEP_TASK_TYPE = "deep_agent_task";

    private final Object deepAgent;
    private final Object dependencies;

    /**
     * Construct executor with dependencies.
     */
    public TaskLoopEventExecutor(Object dependencies, Object deepAgent) {
        this.dependencies = dependencies;
        this.deepAgent = deepAgent;
    }

    /**
     * Execute a task via the inner agent.
     *
     * @param taskId Task identifier
     * @param session Current session
     * @return CompletableFuture with execution result
     */
    public CompletableFuture<ExecutionChunk> executeAbility(String taskId, Object session) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOG.info("[TaskLoopEventExecutor] execute_ability task_id={}", taskId);

                // Execute inner agent invoke
                Map<String, Object> invokeInput = buildInvokeInput(taskId);
                Object result = invokeInnerAgent(invokeInput);

                // Build output chunk
                return buildSuccessChunk(taskId, result);
            } catch (Exception e) {
                LOG.error("[TaskLoopEventExecutor] task_id={} execution failed", taskId, e);
                return buildErrorChunk(taskId, e.getMessage());
            }
        });
    }

    /**
     * Build invoke input for inner agent.
     */
    private Map<String, Object> buildInvokeInput(String taskId) {
        Map<String, Object> input = new HashMap<>();
        input.put("task_id", taskId);
        return input;
    }

    /**
     * Invoke inner agent.
     */
    private Object invokeInnerAgent(Map<String, Object> input) {
        // Placeholder - actual implementation depends on deep agent
        LOG.debug("[TaskLoopEventExecutor] invoke_inner_agent input={}", input);
        return Collections.singletonMap("output", "Task execution result");
    }

    /**
     * Build success chunk.
     */
    private ExecutionChunk buildSuccessChunk(String taskId, Object result) {
        String payload = result != null ? result.toString() : "";
        return ExecutionChunk.success(taskId, payload);
    }

    /**
     * Build error chunk.
     */
    private ExecutionChunk buildErrorChunk(String taskId, String error) {
        return ExecutionChunk.error(taskId, error);
    }

    /**
     * Execution chunk wrapper.
     */
    public static class ExecutionChunk {
        private final String taskId;
        private final boolean success;
        private final String payload;
        private final String error;
        private final Map<String, Object> metadata;

        private ExecutionChunk(String taskId, boolean success, String payload, String error) {
            this.taskId = taskId;
            this.success = success;
            this.payload = payload;
            this.error = error;
            this.metadata = new HashMap<>();
        }

        public static ExecutionChunk success(String taskId, String payload) {
            return new ExecutionChunk(taskId, true, payload, null);
        }

        public static ExecutionChunk error(String taskId, String error) {
            return new ExecutionChunk(taskId, false, null, error);
        }

        public String getTaskId() { return taskId; }
        public boolean isSuccess() { return success; }
        public String getPayload() { return payload; }
        public String getError() { return error; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
}