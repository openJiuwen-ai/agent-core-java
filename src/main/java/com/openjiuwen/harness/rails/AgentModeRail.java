/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.schema.AgentMode;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enforces plan-mode tool restrictions and mode switching.
 *
 * <p>Mirrors Python's {@code AgentModeRail} in
 * {@code openjiuwen/harness/rails/agent_mode_rail.py}.</p>
 */
public class AgentModeRail extends DeepAgentRail {

    public static final Set<String> DEFAULT_PLAN_MODE_ALLOWED_TOOLS = Set.of(
            "switch_mode", "enter_plan_mode", "exit_plan_mode", "ask_user", "task_tool",
            "read_file", "grep", "list_files", "glob", "todo_create", "todo_modify", "todo_list"
    );

    private final Set<String> allowedTools = new LinkedHashSet<>();
    private DeepAgent agent;

    public AgentModeRail() {
        this(DEFAULT_PLAN_MODE_ALLOWED_TOOLS);
    }

    public AgentModeRail(Set<String> allowedTools) {
        setPriority(10);
        this.allowedTools.addAll(allowedTools == null ? DEFAULT_PLAN_MODE_ALLOWED_TOOLS : allowedTools);
    }

    @Override
    public void init(DeepAgent agent) {
        super.init(agent);
        this.agent = agent;
    }

    @Override
    public void uninit(DeepAgent agent) {
        this.agent = null;
    }

    @Override
    public void beforeToolCall(CallbackContext ctx) {
        String toolName = String.valueOf(ctx.getValues().getOrDefault("tool_name", ""));
        if (toolName.isBlank()) {
            return;
        }
        String mode = currentMode(ctx);
        if (AgentMode.PLAN.value().equals(mode) && !allowedTools.contains(toolName)) {
            ctx.reject("Plan mode disallows tool: " + toolName);
            return;
        }
        if (AgentMode.PLAN.value().equals(mode) && isWriteOutsidePlanFile(toolName, ctx)) {
            ctx.reject("Plan mode only allows writes to the configured plan file.");
        }
    }

    public void enterPlanMode(Object session) {
        if (agent != null) {
            agent.switchMode(session, AgentMode.PLAN);
        }
    }

    public void exitPlanMode(Object session) {
        if (agent != null) {
            agent.switchMode(session, AgentMode.NORMAL);
        }
    }

    public Set<String> getAllowedTools() {
        return new LinkedHashSet<>(allowedTools);
    }

    private String currentMode(CallbackContext ctx) {
        Object mode = ctx.get("mode");
        return mode == null ? AgentMode.NORMAL.value() : String.valueOf(mode);
    }

    @SuppressWarnings("unchecked")
    private boolean isWriteOutsidePlanFile(String toolName, CallbackContext ctx) {
        if (!List.of("write_file", "edit_file").contains(toolName)) {
            return false;
        }
        Object args = ctx.get("tool_args");
        if (!(args instanceof Map<?, ?> map)) {
            return true;
        }
        Object rawFilePath = map.containsKey("file_path") ? map.get("file_path") : map.get("path");
        String filePath = rawFilePath == null ? "" : String.valueOf(rawFilePath);
        Object planPath = ctx.get("plan_file_path");
        if (planPath == null || filePath == null || filePath.isBlank()) {
            return true;
        }
        return !Path.of(String.valueOf(planPath)).normalize().equals(Path.of(filePath).normalize());
    }
}
