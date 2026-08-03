/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.subagent;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Session management tools for subagents.
 *
 * <p>Mirrors Python's {@code SessionTaskRow}, {@code SessionToolkit},
 * {@code SessionsListTool}, {@code SessionsCancelTool}, and
 * {@code SessionsSpawnTool} in
 * {@code openjiuwen/harness/tools/subagent/session_tools.py}.</p>
 */
public final class SessionTools {

    public static final String SESSION_SPAWN_TASK_TYPE = "session_spawn_task";

    private SessionTools() {
    }

    public static List<Tool> buildSessionTools(SessionToolkit toolkit) {
        return List.of(new SessionsListTool(toolkit), new SessionsCancelTool(toolkit), new SessionsSpawnTool(toolkit));
    }

    /**
     * Mirrors Python's {@code SessionTaskRow} in
     * {@code openjiuwen/harness/tools/subagent/session_tools.py}.
     */
    public record SessionTaskRow(
            String taskId,
            String subSessionId,
            String description,
            String status,
            String result,
            String error
    ) {
        public SessionTaskRow(String taskId, String status, String title, Map<String, Object> metadata) {
            this(
                    taskId,
                    textValue(metadata == null ? null : metadata.get("sub_session_id")),
                    title,
                    status,
                    textValue(metadata == null ? null : metadata.get("result")),
                    textValue(metadata == null ? null : metadata.get("error"))
            );
        }

        public String title() {
            return description;
        }

        public Map<String, Object> metadata() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("sub_session_id", subSessionId);
            values.put("description", description);
            values.put("result", result);
            values.put("error", error);
            return values;
        }
    }

    /**
     * Mirrors Python's {@code SessionToolkit} in
     * {@code openjiuwen/harness/tools/subagent/session_tools.py}.
     */
    public interface SessionToolkit {
        List<SessionTaskRow> listTasks(Map<String, Object> kwargs) throws Exception;

        Map<String, Object> cancelTask(String taskId, Map<String, Object> kwargs) throws Exception;

        Map<String, Object> spawnTask(String title, String prompt, Map<String, Object> options,
                                      Map<String, Object> kwargs) throws Exception;
    }

    /**
     * Mirrors Python's concrete {@code SessionToolkit} registry in
     * {@code openjiuwen/harness/tools/subagent/session_tools.py}.
     */
    public static class InMemorySessionToolkit implements SessionToolkit {
        private final Map<String, SessionTaskRow> rows = new LinkedHashMap<>();

        public void upsertRunning(String taskId, String subSessionId, String description) {
            rows.put(taskId, new SessionTaskRow(
                    taskId,
                    subSessionId,
                    description,
                    "running",
                    "",
                    ""
            ));
        }

        public void upsert_running(String taskId, String subSessionId, String description) {
            upsertRunning(taskId, subSessionId, description);
        }

        public void markCompleted(String taskId, String result) {
            SessionTaskRow row = rows.get(taskId);
            if (row != null) {
                rows.put(taskId, new SessionTaskRow(
                        row.taskId(),
                        row.subSessionId(),
                        row.description(),
                        "completed",
                        textValue(result),
                        row.error()
                ));
            }
        }

        public void mark_completed(String taskId, String result) {
            markCompleted(taskId, result);
        }

        public void markFailed(String taskId, String error) {
            SessionTaskRow row = rows.get(taskId);
            if (row != null) {
                rows.put(taskId, new SessionTaskRow(
                        row.taskId(),
                        row.subSessionId(),
                        row.description(),
                        "error",
                        row.result(),
                        textValue(error)
                ));
            }
        }

        public void mark_failed(String taskId, String error) {
            markFailed(taskId, error);
        }

        public void markCanceled(String taskId) {
            SessionTaskRow row = rows.get(taskId);
            if (row != null) {
                rows.put(taskId, new SessionTaskRow(
                        row.taskId(),
                        row.subSessionId(),
                        row.description(),
                        "canceled",
                        row.result(),
                        row.error()
                ));
            }
        }

        public void mark_canceled(String taskId) {
            markCanceled(taskId);
        }

        public List<SessionTaskRow> listAll() {
            return new ArrayList<>(rows.values());
        }

        public List<SessionTaskRow> list_all() {
            return listAll();
        }

        public SessionTaskRow get(String taskId) {
            return rows.get(taskId);
        }

        public void clear() {
            rows.clear();
        }

        @Override
        public List<SessionTaskRow> listTasks(Map<String, Object> kwargs) {
            return listAll();
        }

        @Override
        public Map<String, Object> cancelTask(String taskId, Map<String, Object> kwargs) {
            SessionTaskRow row = rows.get(taskId);
            if (row == null) {
                throw new IllegalArgumentException("Task " + taskId + " not found");
            }
            if ("completed".equals(row.status())) {
                return Map.of(
                        "task_id", taskId,
                        "status", "completed",
                        "message", "Task " + taskId + " already completed"
                );
            }
            boolean success = !Boolean.FALSE.equals(kwargs == null ? null : kwargs.get("cancel_success"));
            if (!success) {
                return Map.of(
                        "task_id", taskId,
                        "status", row.status(),
                        "message", "Task " + taskId + " cancel failed"
                );
            }
            markCanceled(taskId);
            return Map.of(
                    "task_id", taskId,
                    "status", "canceled",
                    "message", "Task " + taskId + " cancel success"
            );
        }

        @Override
        public Map<String, Object> spawnTask(String title,
                                             String prompt,
                                             Map<String, Object> options,
                                             Map<String, Object> kwargs) {
            String taskId = UUID.randomUUID().toString().replace("-", "");
            String parentSessionId = Objects.toString(
                    kwargs == null ? null : kwargs.getOrDefault("session_id", "session"),
                    "session"
            );
            String subSessionId = parentSessionId + "_sub_" + taskId.substring(0, 8);
            String description = !textValue(prompt).isEmpty() ? prompt : title;
            upsertRunning(taskId, subSessionId, description);
            return Map.of(
                    "status", "pending",
                    "task_id", taskId,
                    "sub_session_id", subSessionId,
                    "message", "Task " + description + " submitted to background"
            );
        }
    }

    /**
     * Mirrors Python's {@code SessionsListTool} in
     * {@code openjiuwen/harness/tools/subagent/session_tools.py}.
     */
    public static class SessionsListTool extends AbstractHarnessTool {
        private final SessionToolkit toolkit;

        public SessionsListTool(SessionToolkit toolkit) {
            super(toolCard("sessions_list", "sessions_list", "List subagent session tasks."));
            this.toolkit = toolkit;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            if (toolkit == null) {
                return ToolOutput.failure("session toolkit is not configured");
            }
            List<SessionTaskRow> tasks = toolkit.listTasks(kwargs == null ? Map.of() : kwargs);
            if (tasks.isEmpty()) {
                return ToolOutput.success("当前会话没有后台子任务");
            }
            List<String> lines = new ArrayList<>();
            for (SessionTaskRow task : tasks) {
                lines.add(
                        "task_id=" + textValue(task.taskId()) + " | "
                                + "description=" + textValue(task.description()) + " | "
                                + "status=" + textValue(task.status()) + " | "
                                + "result=" + textValue(task.result()) + " | "
                                + "error=" + textValue(task.error())
                );
            }
            return ToolOutput.success(String.join("\n", lines));
        }
    }

    /**
     * Mirrors Python's {@code SessionsCancelTool} in
     * {@code openjiuwen/harness/tools/subagent/session_tools.py}.
     */
    public static class SessionsCancelTool extends AbstractHarnessTool {
        private final SessionToolkit toolkit;

        public SessionsCancelTool(SessionToolkit toolkit) {
            super(toolCard("sessions_cancel", "sessions_cancel", "Cancel a subagent session task."));
            this.toolkit = toolkit;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            if (toolkit == null) {
                return ToolOutput.failure("session toolkit is not configured");
            }
            Map<String, Object> result = toolkit.cancelTask(
                    requiredString(inputs, "task_id"),
                    kwargs == null ? Map.of() : kwargs
            );
            boolean success = Boolean.TRUE.equals(result.get("success"))
                    || "canceled".equals(result.get("status"));
            return ToolOutput.of(success, result, success ? null : textValue(result.get("message")));
        }
    }

    /**
     * Mirrors Python's {@code SessionsSpawnTool} in
     * {@code openjiuwen/harness/tools/subagent/session_tools.py}.
     */
    public static class SessionsSpawnTool extends AbstractHarnessTool {
        private final SessionToolkit toolkit;

        public SessionsSpawnTool(SessionToolkit toolkit) {
            super(toolCard("sessions_spawn", "sessions_spawn", "Spawn a subagent session task."));
            this.toolkit = toolkit;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            if (toolkit == null) {
                return ToolOutput.failure("session toolkit is not configured");
            }
            String title = stringValue(inputs == null ? null : inputs.get("title"));
            if (title.isEmpty()) {
                title = stringValue(inputs == null ? null : inputs.get("subagent_type"));
            }
            String prompt = stringValue(inputs == null ? null : inputs.get("prompt"));
            if (prompt.isEmpty()) {
                prompt = requiredString(inputs, "task_description");
            }
            return ToolOutput.success(toolkit.spawnTask(
                    title,
                    prompt,
                    stringObjectMap(inputs == null ? null : inputs.get("options")),
                    kwargs == null ? Map.of() : kwargs
            ));
        }
    }

    private static String textValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
