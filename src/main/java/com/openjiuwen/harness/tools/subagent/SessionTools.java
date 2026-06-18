/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.subagent;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.List;
import java.util.Map;

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
    public record SessionTaskRow(String taskId, String status, String title, Map<String, Object> metadata) {
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
     * Mirrors Python's {@code SessionsListTool} in
     * {@code openjiuwen/harness/tools/subagent/session_tools.py}.
     */
    public static class SessionsListTool extends AbstractHarnessTool {
        private final SessionToolkit toolkit;

        public SessionsListTool(SessionToolkit toolkit) {
            super(toolCard("sessions_list", "SessionsListTool", "List subagent session tasks."));
            this.toolkit = toolkit;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            if (toolkit == null) {
                return ToolOutput.failure("session toolkit is not configured");
            }
            return ToolOutput.success(Map.of("tasks", toolkit.listTasks(kwargs == null ? Map.of() : kwargs)));
        }
    }

    /**
     * Mirrors Python's {@code SessionsCancelTool} in
     * {@code openjiuwen/harness/tools/subagent/session_tools.py}.
     */
    public static class SessionsCancelTool extends AbstractHarnessTool {
        private final SessionToolkit toolkit;

        public SessionsCancelTool(SessionToolkit toolkit) {
            super(toolCard("sessions_cancel", "SessionsCancelTool", "Cancel a subagent session task."));
            this.toolkit = toolkit;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            if (toolkit == null) {
                return ToolOutput.failure("session toolkit is not configured");
            }
            return ToolOutput.success(toolkit.cancelTask(requiredString(inputs, "task_id"),
                    kwargs == null ? Map.of() : kwargs));
        }
    }

    /**
     * Mirrors Python's {@code SessionsSpawnTool} in
     * {@code openjiuwen/harness/tools/subagent/session_tools.py}.
     */
    public static class SessionsSpawnTool extends AbstractHarnessTool {
        private final SessionToolkit toolkit;

        public SessionsSpawnTool(SessionToolkit toolkit) {
            super(toolCard("sessions_spawn", "SessionsSpawnTool", "Spawn a subagent session task."));
            this.toolkit = toolkit;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            if (toolkit == null) {
                return ToolOutput.failure("session toolkit is not configured");
            }
            return ToolOutput.success(toolkit.spawnTask(
                    stringValue(inputs == null ? null : inputs.get("title")),
                    requiredString(inputs, "prompt"),
                    stringObjectMap(inputs == null ? null : inputs.get("options")),
                    kwargs == null ? Map.of() : kwargs
            ));
        }
    }
}
