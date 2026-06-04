/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import java.util.function.Function;
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
                if (result instanceof Map<?, ?> rawMap
                        && "error".equals(String.valueOf(rawMap.get("result_type")))) {
                    @SuppressWarnings("unchecked")
                    Map<Object, Object> map = (Map<Object, Object>) rawMap;
                    Object errorValue = map.containsKey("error") ? map.get("error") : "";
                    return buildErrorChunk(taskId, String.valueOf(errorValue));
                }
                return buildSuccessChunk(taskId, result);
            } catch (Exception e) {
                LOG.error("[TaskLoopEventExecutor] task_id={} execution failed", taskId, e);
                return buildErrorChunk(taskId, e.getMessage());
            }
        });
    }

    /**
     * Cancellation is always supported for the task-loop executor.
     *
     * <p>Mirrors Python's {@code cancel()} behaviour by aborting the owning
     * coordinator when a DeepAgent is attached.</p>
     */
    public boolean cancel(String taskId, Object session) {
        LOG.info("[TaskLoopEventExecutor] cancel task_id={}", taskId);
        if (deepAgent instanceof com.openjiuwen.harness.DeepAgent agent) {
            Object state = session instanceof com.openjiuwen.core.session.Session typedSession
                    ? agent.loadState(typedSession)
                    : null;
            if (state instanceof com.openjiuwen.harness.schema.DeepAgentState deepState
                    && deepState.getTaskPlan() != null) {
                deepState.getTaskPlan().markCancelled(taskId, "cancelled");
            }
        }
        if (deepAgent instanceof com.openjiuwen.harness.DeepAgent agent) {
            Object coordinator = lookupField(agent, "loopCoordinator");
            if (coordinator == null) {
                coordinator = lookupField(agent, "_loopCoordinator");
            }
            if (coordinator instanceof LoopCoordinator loopCoordinator) {
                loopCoordinator.requestAbort();
            }
        }
        return true;
    }

    /**
     * Builder factory used by task-loop registry code.
     */
    public static Function<Object, TaskLoopEventExecutor> buildDeepExecutor(Object deepAgent) {
        return deps -> new TaskLoopEventExecutor(deps, deepAgent);
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
     * Build invoke input for inner agent.
     */
    protected Map<String, Object> buildInvokeInput(String taskId) {
        Map<String, Object> input = new HashMap<>();
        input.put("task_id", taskId);
        return input;
    }

    /**
     * Invoke inner agent.
     * <p>
     * Mirrors Python's ReAct loop execution logic:
     * {@code result = await agent.react_agent.invoke(effective, session, _streaming=True)}
     */
    protected Object invokeInnerAgent(Map<String, Object> input) {
        LOG.debug("[TaskLoopEventExecutor] invoke_inner_agent input={}", input);
        
        if (deepAgent instanceof com.openjiuwen.harness.DeepAgent da) {
            try {
                // Get the inner ReAct agent (delegate)
                com.openjiuwen.core.singleagent.agents.ReActAgent reactAgent = da.getDelegate();
                if (reactAgent == null) {
                    LOG.warn("[TaskLoopEventExecutor] react_agent delegate is null, returning empty result");
                    Map<String, Object> emptyResult = new HashMap<>();
                    emptyResult.put("output", "");
                    emptyResult.put("result_type", "error");
                    emptyResult.put("error", "No react_agent configured");
                    return emptyResult;
                }
                
                // Build effective input with query and conversation_id
                Map<String, Object> effective = new HashMap<>(input);
                
                // Fire before_task_iteration callback if available
                fireBeforeTaskIteration(da, effective);
                
                // Invoke the inner ReAct agent with a session
                String sessionId = (String) effective.getOrDefault("conversation_id", "");
                com.openjiuwen.core.session.Session session = 
                    new com.openjiuwen.core.session.internal.AgentTeamSession(sessionId, "deep_agent");
                Object result = reactAgent.invoke(effective, session);
                
                // Fire after_task_iteration callback if available
                fireAfterTaskIteration(da, result);
                
                return result;
            } catch (Exception e) {
                LOG.error("[TaskLoopEventExecutor] invoke_inner_agent failed", e);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("output", "");
                errorResult.put("result_type", "error");
                errorResult.put("error", e.getMessage());
                return errorResult;
            }
        }
        
        // Fallback result when deep agent is not configured
        LOG.warn("[TaskLoopEventExecutor] deep_agent not properly configured");
        Map<String, Object> fallbackResult = new HashMap<>();
        fallbackResult.put("output", "");
        fallbackResult.put("result_type", "error");
        fallbackResult.put("error", "DeepAgent not configured");
        return fallbackResult;
    }
    
    /**
     * Fire before_task_iteration callback on agent rails.
     */
    private void fireBeforeTaskIteration(com.openjiuwen.harness.DeepAgent agent, Map<String, Object> input) {
        try {
            // Notify rails about task iteration start
            agent.fireCallback("before_task_iteration", input);
        } catch (Exception e) {
            LOG.debug("[TaskLoopEventExecutor] before_task_iteration callback skipped", e);
        }
    }
    
    /**
     * Fire after_task_iteration callback on agent rails.
     */
    private void fireAfterTaskIteration(com.openjiuwen.harness.DeepAgent agent, Object result) {
        try {
            Map<String, Object> callbackInput = new HashMap<>();
            callbackInput.put("result", result);
            agent.fireCallback("after_task_iteration", callbackInput);
        } catch (Exception e) {
            LOG.debug("[TaskLoopEventExecutor] after_task_iteration callback skipped", e);
        }
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
