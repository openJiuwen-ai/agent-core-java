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
     */
    private Map<String, Object> getTaskMetadata(String taskId) {
        // Placeholder - actual implementation depends on task manager
        return new ConcurrentHashMap<>();
    }

    /**
     * Create subagent instance.
     */
    private Object createSubagent(String subagentType, String sessionId) {
        // Placeholder - actual implementation depends on deep agent
        LOG.debug("[SessionSpawnExecutor] create_subagent type={}, session_id={}", subagentType, sessionId);
        return null;
    }

    /**
     * Invoke subagent with query.
     */
    private Object invokeSubagent(Object subagent, String query, String sessionId) {
        // Placeholder - actual implementation depends on subagent interface
        LOG.debug("[SessionSpawnExecutor] invoke_subagent query={}, session_id={}", query, sessionId);
        return Collections.singletonMap("output", "Subagent result placeholder");
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