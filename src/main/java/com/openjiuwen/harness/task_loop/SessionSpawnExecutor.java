/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session spawn executor — executes SESSION_SPAWN_TASK_TYPE tasks.
 *
 * <p>This executor creates a subagent instance and invokes it with the
 * task description, then yields the result as a TASK_COMPLETION event.
 *
 * <p>Mirrors Python's {@code SessionSpawnExecutor} in
 * {@code openjiuwen.harness.task_loop.session_spawn_executor}.
 */
public class SessionSpawnExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(SessionSpawnExecutor.class);

    /** Task type for session spawn. */
    public static final String SESSION_SPAWN_TASK_TYPE = "session_spawn";

    private final Object deepAgent;
    private final Object taskManager;

    /**
     * Construct executor with dependencies.
     */
    public SessionSpawnExecutor(Object deepAgent, Object taskManager) {
        this.deepAgent = deepAgent;
        this.taskManager = taskManager;
    }

    /**
     * Execute subagent task.
     *
     * @param taskId Task identifier
     * @param session Current session
     * @return CompletableFuture with execution result
     */
    public CompletableFuture<ExecutionResult> executeAbility(String taskId, Object session) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOG.info("[SessionSpawnExecutor] Executing task_id={}", taskId);

                // Get task metadata
                Map<String, Object> meta = getTaskMetadata(taskId);
                if (meta == null) {
                    return ExecutionResult.error(taskId, "Task not found");
                }

                String subagentType = (String) meta.getOrDefault("subagent_type", "general-purpose");
                String query = (String) meta.getOrDefault("task_description", "");
                String cid = (String) meta.getOrDefault("sub_session_id", "");

                LOG.info("[SessionSpawnExecutor] subagent_type={}, sub_session_id={}", subagentType, cid);

                // Create and invoke subagent
                Object subagent = createSubagent(subagentType, cid);
                Object result = invokeSubagent(subagent, query, cid);

                String payload = extractPayload(result);

                LOG.info("[SessionSpawnExecutor] task_id={} completed with payload length={}",
                        taskId, payload != null ? payload.length() : 0);

                return ExecutionResult.success(taskId, payload);
            } catch (Exception e) {
                LOG.error("[SessionSpawnExecutor] task_id={} execution failed", taskId, e);
                return ExecutionResult.error(taskId, e.getMessage());
            }
        });
    }

    /**
     * Get task metadata from task manager.
     * <p>
     * Mirrors Python's: {@code await self._task_manager.get_task(TaskFilter(task_id=task_id))}
     */
    private Map<String, Object> getTaskMetadata(String taskId) {
        if (taskManager instanceof com.openjiuwen.core.common.task_manager.TaskManager tm) {
            try {
                // Use getTask to query task by ID directly
                com.openjiuwen.core.common.task_manager.Task task = tm.getTask(taskId);
                if (task != null) {
                    Map<String, Object> metadata = task.getMetadata();
                    return metadata != null ? new ConcurrentHashMap<>(metadata) : new ConcurrentHashMap<>();
                }
            } catch (Exception e) {
                LOG.warn("[SessionSpawnExecutor] Failed to get task metadata for task_id={}", taskId, e);
            }
        }
        return new ConcurrentHashMap<>();
    }

    /**
     * Create subagent instance.
     * <p>
     * Mirrors Python's: {@code self._deep_agent.create_subagent(subagent_type, cid)}
     * Note: Currently uses a placeholder implementation as subagent creation is deferred.
     */
    private Object createSubagent(String subagentType, String sessionId) {
        LOG.debug("[SessionSpawnExecutor] create_subagent type={}, session_id={}", subagentType, sessionId);
        
        if (deepAgent instanceof com.openjiuwen.harness.DeepAgent da) {
            // Return the DeepAgent itself as a placeholder for subagent creation
            // Full subagent factory implementation is deferred
            LOG.info("[SessionSpawnExecutor] Using DeepAgent delegate as subagent placeholder");
            return da.getDelegate();
        }
        throw new IllegalStateException("DeepAgent not properly configured for subagent creation");
    }

    /**
     * Invoke subagent with query.
     * <p>
     * Mirrors Python's: {@code result = await subagent.invoke({"query": query, "conversation_id": cid})}
     */
    private Object invokeSubagent(Object subagent, String query, String sessionId) {
        LOG.debug("[SessionSpawnExecutor] invoke_subagent query={}, session_id={}", query, sessionId);
        
        if (subagent instanceof com.openjiuwen.core.singleagent.BaseAgent agent) {
            try {
                // Build invocation input map
                Map<String, Object> input = new HashMap<>();
                input.put("query", query);
                input.put("conversation_id", sessionId);
                
                // Create a simple AgentTeamSession for invocation
                com.openjiuwen.core.session.Session session = 
                    new com.openjiuwen.core.session.internal.AgentTeamSession(sessionId, "subagent");
                
                // Invoke agent and return result
                Object result = agent.invoke(input, session);
                return result;
            } catch (Exception e) {
                LOG.error("[SessionSpawnExecutor] Failed to invoke subagent", e);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("output", "");
                errorResult.put("error", e.getMessage());
                return errorResult;
            }
        }
        
        // Fallback: return placeholder result
        LOG.warn("[SessionSpawnExecutor] Subagent not a BaseAgent, using fallback");
        Map<String, Object> fallbackResult = new HashMap<>();
        fallbackResult.put("output", "");
        fallbackResult.put("error", "Subagent invocation not supported");
        return fallbackResult;
    }

    /**
     * Extract payload from result.
     */
    private String extractPayload(Object result) {
        if (result instanceof Map) {
            Object output = ((Map<?, ?>) result).get("output");
            return output != null ? output.toString() : "";
        }
        return result != null ? result.toString() : "";
    }

    /**
     * Execution result wrapper.
     */
    public static class ExecutionResult {
        private final String taskId;
        private final boolean success;
        private final String payload;
        private final String error;

        private ExecutionResult(String taskId, boolean success, String payload, String error) {
            this.taskId = taskId;
            this.success = success;
            this.payload = payload;
            this.error = error;
        }

        public static ExecutionResult success(String taskId, String payload) {
            return new ExecutionResult(taskId, true, payload, null);
        }

        public static ExecutionResult error(String taskId, String error) {
            return new ExecutionResult(taskId, false, null, error);
        }

        public String getTaskId() { return taskId; }
        public boolean isSuccess() { return success; }
        public String getPayload() { return payload; }
        public String getError() { return error; }
    }
}