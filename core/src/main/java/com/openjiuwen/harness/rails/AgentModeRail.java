/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.SystemPromptBuilder;
import com.openjiuwen.harness.prompts.sections.AgentModeSection;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.schema.AgentMode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Enforces plan-mode tool restrictions and mode switching.
 *
 * <p>Mirrors Python's {@code AgentModeRail} in
 * {@code openjiuwen/harness/rails/agent_mode_rail.py}.</p>
 */
public class AgentModeRail extends DeepAgentRail {

    private static final Set<String> TODO_TOOL_NAMES = Set.of("todo_create", "todo_list", "todo_modify");
    private static final Set<String> SESSION_TOOL_NAMES = Set.of("sessions_list", "sessions_cancel", "sessions_spawn");
    private static final Set<String> HIDDEN_IN_PLAN = union(TODO_TOOL_NAMES, SESSION_TOOL_NAMES);
    private static final Set<String> HIDDEN_IN_NORMAL = Set.of("enter_plan_mode", "exit_plan_mode");
    private static final Set<String> PLAN_FILE_WRITE_TOOLS = Set.of("write_file", "edit_file");
    private static final Pattern GIT_WRITE_PATTERN = Pattern.compile(
            "\\bgit\\s+(add|commit|push|pull|reset\\s+--hard|checkout\\s+--\\.|clean\\s+-[a-zA-Z]*f|"
                    + "stash\\s+(drop|clear)|branch\\s+-D|merge|tag|amend|rebase)\\b"
    );

    public static final Set<String> DEFAULT_PLAN_MODE_ALLOWED_TOOLS = Set.of(
            "switch_mode", "enter_plan_mode", "exit_plan_mode", "ask_user", "task_tool",
            "read_file", "grep", "list_files", "glob", "bash", "write_file", "edit_file"
    );

    private final Set<String> allowedTools = new LinkedHashSet<>();
    private DeepAgent agent;
    private SystemPromptBuilder systemPromptBuilder;
    private boolean ownsTaskTool;
    private final Set<String> ownedTaskToolNames = new LinkedHashSet<>();

    public AgentModeRail() {
        this(DEFAULT_PLAN_MODE_ALLOWED_TOOLS);
    }

    public AgentModeRail(Set<String> allowedTools) {
        setPriority(85);
        this.allowedTools.addAll(allowedTools == null ? DEFAULT_PLAN_MODE_ALLOWED_TOOLS : allowedTools);
    }

    @Override
    public void init(DeepAgent agent) {
        super.init(agent);
        this.agent = agent;
        String language = agent == null || agent.deepConfig() == null ? "cn" : agent.deepConfig().getLanguage();
        this.systemPromptBuilder = new SystemPromptBuilder(language, null);
    }

    @Override
    public void uninit(DeepAgent agent) {
        if (ownsTaskTool && agent != null) {
            unregisterTaskTool(agent);
        }
        if (systemPromptBuilder != null) {
            systemPromptBuilder.removeSection(SectionName.MODE_INSTRUCTIONS);
        }
        this.agent = null;
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (ctx == null || systemPromptBuilder == null) {
            return;
        }
        String mode = currentMode(ctx);
        if (!AgentMode.PLAN.value().equals(mode)) {
            systemPromptBuilder.removeSection(SectionName.MODE_INSTRUCTIONS);
            filterTools(ctx, HIDDEN_IN_NORMAL);
            syncTaskToolForModelTools(ctx);
            return;
        }

        String planFilePath = planFilePath(ctx);
        PromptSectionHolder section = new PromptSectionHolder(AgentModeSection.buildPlanModeSection(
                systemPromptBuilder.getLanguage(),
                planFilePath,
                planFilePath != null && !planFilePath.isBlank() && Path.of(planFilePath).toFile().exists()
        ));
        systemPromptBuilder.addSection(section.section());
        systemPromptBuilder.removeSection(SectionName.TODO);
        systemPromptBuilder.removeSection(SectionName.SESSION_TOOLS);
        ctx.put("mode_section", section.section());
        filterTools(ctx, HIDDEN_IN_PLAN);
        syncTaskToolForModelTools(ctx);
    }

    @Override
    public void beforeToolCall(CallbackContext ctx) {
        String toolName = String.valueOf(ctx.getValues().getOrDefault("tool_name", ""));
        if (toolName.isBlank()) {
            return;
        }
        if ("enter_plan_mode".equals(toolName)) {
            handleEnter(ctx);
            return;
        }
        if ("exit_plan_mode".equals(toolName)) {
            handleExit(ctx);
            return;
        }
        String mode = currentMode(ctx);
        if (!AgentMode.PLAN.value().equals(mode)) {
            return;
        }
        if (Boolean.TRUE.equals(ctx.get("_skip_tool"))) {
            return;
        }
        if (HIDDEN_IN_PLAN.contains(toolName)) {
            rejectTool(ctx, "[AgentModeRail] Tool '" + toolName + "' is hidden in plan mode.");
            return;
        }
        if (!allowedTools.contains(toolName)) {
            rejectTool(ctx, "[AgentModeRail] Tool '" + toolName + "' is not available in plan mode.");
            return;
        }
        if ("bash".equals(toolName) && GIT_WRITE_PATTERN.matcher(commandArg(ctx)).find()) {
            rejectTool(ctx, "[AgentModeRail] Git write operations are blocked in plan mode ("
                    + commandArg(ctx) + ").");
            return;
        }
        if (isWriteOutsidePlanFile(toolName, ctx)) {
            rejectTool(ctx, "[AgentModeRail] '" + toolName + "' can only target the plan file ("
                    + planFilePath(ctx) + ").");
        }
    }

    @Override
    public void afterToolCall(CallbackContext ctx) {
        if (ctx == null || Boolean.TRUE.equals(ctx.get("_skip_tool"))) {
            return;
        }
        String toolName = String.valueOf(ctx.getValues().getOrDefault("tool_name", ""));
        if ("enter_plan_mode".equals(toolName) && agent != null) {
            registerTaskTool(agent);
        } else if ("exit_plan_mode".equals(toolName) && agent != null) {
            unregisterTaskTool(agent);
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

    public SystemPromptBuilder getSystemPromptBuilder() {
        return systemPromptBuilder;
    }

    void setSystemPromptBuilder(SystemPromptBuilder systemPromptBuilder) {
        this.systemPromptBuilder = systemPromptBuilder;
    }

    boolean ownsTaskTool() {
        return ownsTaskTool;
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
        String planPath = planFilePath(ctx);
        if (planPath == null || planPath.isBlank() || filePath == null || filePath.isBlank()) {
            return true;
        }
        return !Path.of(planPath).normalize().equals(Path.of(filePath).normalize());
    }

    private void handleEnter(CallbackContext ctx) {
        if (!AgentMode.PLAN.value().equals(currentMode(ctx))) {
            rejectTool(ctx, "[AgentModeRail] enter_plan_mode can only be called in plan mode. "
                    + "Use the switch_mode tool to switch to plan mode.");
        }
    }

    private void handleExit(CallbackContext ctx) {
        if (!AgentMode.PLAN.value().equals(currentMode(ctx))) {
            rejectTool(ctx, "[AgentModeRail] exit_plan_mode can only be called in plan mode.");
        }
    }

    private void rejectTool(CallbackContext ctx, String message) {
        ctx.put("_skip_tool", true);
        ctx.put("tool_result", Map.of("error", message));
        ctx.reject(message);
    }

    private String planFilePath(CallbackContext ctx) {
        Object planPath = ctx == null ? null : ctx.get("plan_file_path");
        if (planPath != null) {
            return String.valueOf(planPath);
        }
        if (agent != null) {
            String path = agent.getPlanFilePath(ctx == null ? null : ctx.get("session"));
            return path == null ? "" : path;
        }
        return "";
    }

    private String commandArg(CallbackContext ctx) {
        Object args = ctx.get("tool_args");
        if (args instanceof Map<?, ?> map) {
            Object command = map.get("command");
            return command == null ? "" : String.valueOf(command);
        }
        return "";
    }

    private void filterTools(CallbackContext ctx, Set<String> hiddenNames) {
        Object tools = ctx.get("tools");
        if (!(tools instanceof List<?> list)) {
            return;
        }
        List<Object> filtered = new ArrayList<>();
        for (Object tool : list) {
            if (!hiddenNames.contains(toolName(tool))) {
                filtered.add(tool);
            }
        }
        ctx.put("tools", filtered);
    }

    @SuppressWarnings("unchecked")
    private void syncTaskToolForModelTools(CallbackContext ctx) {
        Object tools = ctx.get("tools");
        if (!(tools instanceof List<?> list)) {
            return;
        }
        List<Object> updated = new ArrayList<>((List<Object>) list);
        if (ownsTaskTool) {
            boolean exists = updated.stream().anyMatch(tool -> ownedTaskToolNames.contains(toolName(tool)));
            if (!exists) {
                updated.add(ToolInfo.builder().name("task_tool").description("Run a subagent task.").build());
            }
            ctx.put("tools", updated);
            return;
        }
        if (!ownedTaskToolNames.isEmpty()) {
            updated.removeIf(tool -> ownedTaskToolNames.contains(toolName(tool)));
            ownedTaskToolNames.clear();
            ctx.put("tools", updated);
        }
    }

    private void registerTaskTool(DeepAgent agent) {
        if (ownsTaskTool || agent == null || agent.deepConfig() == null || agent.deepConfig().getSubagents().isEmpty()) {
            return;
        }
        if (agent.getTools().containsKey("task_tool")) {
            return;
        }
        agent.registerTool(new PlanModeTaskTool());
        ownedTaskToolNames.clear();
        ownedTaskToolNames.add("task_tool");
        ownsTaskTool = true;
    }

    private void unregisterTaskTool(DeepAgent agent) {
        if (!ownsTaskTool || agent == null) {
            return;
        }
        agent.unregisterTool("task_tool");
        ownsTaskTool = false;
    }

    private static String toolName(Object tool) {
        if (tool instanceof ToolInfo info) {
            return info.getName();
        }
        if (tool instanceof Tool value && value.getCard() != null) {
            return value.getCard().getName();
        }
        if (tool instanceof Map<?, ?> map && map.containsKey("name")) {
            return String.valueOf(map.get("name"));
        }
        return tool == null ? "" : String.valueOf(tool);
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
    }

    private record PromptSectionHolder(PromptSection section) {
    }

    private static final class PlanModeTaskTool extends Tool {
        private PlanModeTaskTool() {
            super(new ToolCard("task_tool", "task_tool", "Run a subagent task."));
        }
    }
}
