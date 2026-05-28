/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.agent_control;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session tools for async subagent spawning.
 *
 * <p>Mirrors Python's {@code session_tools} module in
 * {@code openjiuwen.harness.tools.agent_control.session_tools}.
 */
public final class SessionTools {

    private static final Logger LOG = LoggerFactory.getLogger(SessionTools.class);

    /** Task type for session spawn. */
    public static final String SESSION_SPAWN_TASK_TYPE = "session_spawn_task";

    /**
     * Session task row (business view).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionTaskRow {
        private String taskId;
        private String subSessionId;
        private String description;
        private String status;
        @Builder.Default
        private String result = "";
        @Builder.Default
        private String error = "";
    }

    /**
     * Session task registry.
     * <p>
     * Mirrors Python's {@code SessionToolkit} class.
     */
    public static class SessionToolkit {
        private final Map<String, SessionTaskRow> rows = new ConcurrentHashMap<>();

        /**
         * Insert or update task as running.
         */
        public void upsertRunning(String taskId, String subSessionId, String description) {
            SessionTaskRow row = SessionTaskRow.builder()
                    .taskId(taskId)
                    .subSessionId(subSessionId)
                    .description(description)
                    .status("running")
                    .build();
            rows.put(taskId, row);
            LOG.debug("[SessionToolkit] upsert_running task_id={}", taskId);
        }

        /**
         * Mark task as completed with result.
         */
        public void markCompleted(String taskId, String result) {
            SessionTaskRow row = rows.get(taskId);
            if (row != null) {
                row.setStatus("completed");
                row.setResult(result != null ? result : "");
                LOG.debug("[SessionToolkit] mark_completed task_id={}", taskId);
            }
        }

        /**
         * Mark task as failed with error.
         */
        public void markFailed(String taskId, String error) {
            SessionTaskRow row = rows.get(taskId);
            if (row != null) {
                row.setStatus("error");
                row.setError(error != null ? error : "");
                LOG.debug("[SessionToolkit] mark_failed task_id={}", taskId);
            }
        }

        /**
         * Mark task as canceled.
         */
        public void markCanceled(String taskId) {
            SessionTaskRow row = rows.get(taskId);
            if (row != null) {
                row.setStatus("canceled");
                LOG.debug("[SessionToolkit] mark_canceled task_id={}", taskId);
            }
        }

        /**
         * List all tasks.
         */
        public List<SessionTaskRow> listAll() {
            return new ArrayList<>(rows.values());
        }

        /**
         * Get task by id.
         */
        public SessionTaskRow get(String taskId) {
            return rows.get(taskId);
        }

        /**
         * Clear all tasks.
         */
        public void clear() {
            rows.clear();
            LOG.debug("[SessionToolkit] clear");
        }

        /**
         * Get count of tasks.
         */
        public int size() {
            return rows.size();
        }
    }

    /**
     * Sessions list tool.
     * <p>
     * Mirrors Python's {@code SessionsListTool}.
     */
    public static class SessionsListTool {
        private final SessionToolkit toolkit;
        private final String language;

        public SessionsListTool(SessionToolkit toolkit, String language) {
            this.toolkit = toolkit;
            this.language = language;
        }

        public ToolOutput invoke(Map<String, Object> inputs) {
            List<SessionTaskRow> tasks = toolkit.listAll();
            StringBuilder sb = new StringBuilder();

            for (SessionTaskRow task : tasks) {
                sb.append(String.format(
                    "task_id=%s | description=%s | status=%s | result=%s | error=%s\n",
                    task.getTaskId(),
                    task.getDescription(),
                    task.getStatus(),
                    task.getResult(),
                    task.getError()
                ));
            }

            if (sb.length() == 0) {
                String emptyMsg = language.equals("cn") ?
                    "当前会话没有后台子任务" : "No background tasks for this session";
                sb.append(emptyMsg);
            }

            return ToolOutput.success(sb.toString());
        }
    }

    /**
     * Sessions cancel tool.
     * <p>
     * Mirrors Python's {@code SessionsCancelTool}.
     */
    public static class SessionsCancelTool {
        private final Object parentAgent;
        private final SessionToolkit toolkit;
        private final String language;

        public SessionsCancelTool(Object parentAgent, SessionToolkit toolkit, String language) {
            this.parentAgent = parentAgent;
            this.toolkit = toolkit;
            this.language = language;
        }

        public ToolOutput invoke(Map<String, Object> inputs) {
            String taskId = inputs.getOrDefault("task_id", "").toString();

            if (taskId.isEmpty()) {
                String errorMsg = language.equals("cn") ?
                    "缺少 task_id 参数" : "Missing required parameter: task_id";
                return ToolOutput.error(errorMsg);
            }

            SessionTaskRow row = toolkit.get(taskId);
            if (row == null) {
                String errorMsg = language.equals("cn") ?
                    "找不到任务: " + taskId : "Task not found: " + taskId;
                return ToolOutput.error(errorMsg);
            }

            if (!row.getStatus().equals("running")) {
                String errorMsg = language.equals("cn") ?
                    "任务已完成或已取消: " + taskId : "Task already completed or canceled: " + taskId;
                return ToolOutput.error(errorMsg);
            }

            // Cancel task
            toolkit.markCanceled(taskId);

            // Try to cancel via agent if available
            try {
                if (parentAgent instanceof com.openjiuwen.harness.DeepAgent da) {
                    da.cancelTask(taskId);
                }
            } catch (Exception e) {
                LOG.debug("[SessionsCancelTool] agent cancel failed", e);
            }

            String successMsg = language.equals("cn") ?
                "任务已取消: " + taskId : "Task canceled: " + taskId;
            return ToolOutput.success(successMsg);
        }
    }

    /**
     * Sessions read tool.
     * <p>
     * Mirrors Python's SessionsReadTool.
     */
    public static class SessionsReadTool {
        private final SessionToolkit toolkit;
        private final String language;

        public SessionsReadTool(SessionToolkit toolkit, String language) {
            this.toolkit = toolkit;
            this.language = language;
        }

        public ToolOutput invoke(Map<String, Object> inputs) {
            String taskId = inputs.getOrDefault("task_id", "").toString();

            if (taskId.isEmpty()) {
                String errorMsg = language.equals("cn") ?
                    "缺少 task_id 参数" : "Missing required parameter: task_id";
                return ToolOutput.error(errorMsg);
            }

            SessionTaskRow row = toolkit.get(taskId);
            if (row == null) {
                String errorMsg = language.equals("cn") ?
                    "找不到任务: " + taskId : "Task not found: " + taskId;
                return ToolOutput.error(errorMsg);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("task_id", row.getTaskId());
            data.put("description", row.getDescription());
            data.put("status", row.getStatus());
            data.put("result", row.getResult());
            data.put("error", row.getError());

            return ToolOutput.success(data);
        }
    }

    /**
     * Sessions spawn tool.
     * <p>
     * Mirrors Python's SpawnTaskTool.
     */
    public static class SessionsSpawnTool {
        private final Object parentAgent;
        private final SessionToolkit toolkit;
        private final String language;

        public SessionsSpawnTool(Object parentAgent, SessionToolkit toolkit, String language) {
            this.parentAgent = parentAgent;
            this.toolkit = toolkit;
            this.language = language;
        }

        public ToolOutput invoke(Map<String, Object> inputs) {
            String description = inputs.getOrDefault("description", "").toString();
            String subagentType = inputs.getOrDefault("subagent_type", "general-purpose").toString();

            if (description.isEmpty()) {
                String errorMsg = language.equals("cn") ?
                    "缺少 description 参数" : "Missing required parameter: description";
                return ToolOutput.error(errorMsg);
            }

            // Generate task ID
            String taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);
            String subSessionId = "sub_" + UUID.randomUUID().toString().substring(0, 8);

            // Register task
            toolkit.upsertRunning(taskId, subSessionId, description);

            // Spawn task via agent
            try {
                if (parentAgent instanceof com.openjiuwen.harness.DeepAgent da) {
                    da.spawnSubagentTask(taskId, subagentType, description, subSessionId);
                }
            } catch (Exception e) {
                toolkit.markFailed(taskId, e.getMessage());
                return ToolOutput.error(language.equals("cn") ?
                    "任务启动失败: " + e.getMessage() : "Task spawn failed: " + e.getMessage());
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("task_id", taskId);
            data.put("sub_session_id", subSessionId);
            data.put("status", "running");
            data.put("message", language.equals("cn") ?
                "任务已启动" : "Task spawned");

            return ToolOutput.success(data);
        }
    }

    /**
     * Create session tools for the given toolkit.
     */
    public static List<Object> createSessionTools(Object parentAgent, SessionToolkit toolkit, String language) {
        List<Object> tools = new ArrayList<>();
        tools.add(new SessionsListTool(toolkit, language));
        tools.add(new SessionsCancelTool(parentAgent, toolkit, language));
        tools.add(new SessionsReadTool(toolkit, language));
        tools.add(new SessionsSpawnTool(parentAgent, toolkit, language));
        return tools;
    }

    /**
     * Tool output wrapper.
     */
    @Data
    @Builder
    public static class ToolOutput {
        private boolean success;
        private Object data;
        private String error;

        public static ToolOutput success(Object data) {
            return ToolOutput.builder().success(true).data(data).build();
        }

        public static ToolOutput error(String error) {
            return ToolOutput.builder().success(false).error(error).build();
        }
    }
}