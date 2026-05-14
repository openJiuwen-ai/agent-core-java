/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.DeepAgentConfig;

import java.util.Map;

/**
 * Lists async session metadata registered by SessionRail.
 *
 * <p>Mirrors Python's session-listing behaviors in
 * {@code openjiuwen.harness.rails.subagent.session_rail}.
 */
public class SessionsListTool extends AbstractHarnessTool {

    private final DeepAgentConfig.SessionToolkit toolkit;
    private final String language;

    public SessionsListTool(DeepAgentConfig.SessionToolkit toolkit) {
        this(toolkit, "en");
    }

    public SessionsListTool(DeepAgentConfig.SessionToolkit toolkit, String language) {
        super(toolCard("harness.sessions.list", "sessions_list", "List background or delegated agent sessions."), null);
        this.toolkit = toolkit;
        this.language = language == null ? "en" : language;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        java.util.List<Map<String, Object>> tasks = toolkit != null ? toolkit.listTasks() : java.util.List.of();
        if (tasks.isEmpty()) {
            String message = "cn".equalsIgnoreCase(language) ? "当前没有后台会话。" : "No background sessions.";
            return new ToolOutput(true, message, null);
        }
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> row : tasks) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(row.getOrDefault("task_id", ""))
                    .append(" | ")
                    .append(row.getOrDefault("sub_session_id", ""))
                    .append(" | ")
                    .append(row.getOrDefault("description", ""))
                    .append(" | ")
                    .append(row.getOrDefault("status", ""));
        }
        return new ToolOutput(true, builder.toString(), null);
    }
}
