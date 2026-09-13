/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataRegistry;
import com.openjiuwen.harness.schema.AgentMode;
import com.openjiuwen.harness.tools.SwitchModeTool;
import com.openjiuwen.harness.tools.TaskTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Public class AgentModeRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class AgentModeRail extends DeepAgentRail {
    /**
     * PLAN_MODE_SECTION.
     * 
     * @since 0.1.7
     */
    public static final String PLAN_MODE_SECTION = "agent_mode_plan";

    /**
     * PLAN_MODE_SECTION_PRIORITY.
     * 
     * @since 0.1.7
     */
    public static final int PLAN_MODE_SECTION_PRIORITY = 35;

    /**
     * Set.of.
     * 
     * @since 0.1.7
     */
    private static final Set<String> HIDDEN_IN_NORMAL = Set.of("enter_plan_mode", "exit_plan_mode");

    /**
     * Set.of.
     * 
     * @since 0.1.7
     */
    private static final Set<String> HIDDEN_IN_PLAN =
        Set.of("todo_create", "todo_list", "todo_modify", "sessions_list", "sessions_cancel", "sessions_spawn");

    /**
     * Set.of.
     * 
     * @since 0.1.7
     */
    private static final Set<String> PLAN_WRITE_TOOLS = Set.of("write_file", "edit_file");

    /**
     * Set.of.
     * 
     * @since 0.1.7
     */
    private static final Set<String> DEFAULT_PLAN_ALLOWED_TOOLS =
        Set.of("switch_mode", "enter_plan_mode", "exit_plan_mode", "ask_user", "task_tool", "read_file", "grep",
                "list_files", "glob", "bash", "write_file", "edit_file");

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<Tool> tools = new ArrayList<>();
    private final Set<String> allowedTools;
    private Tool ownedTaskTool;
    private DeepAgent owner;

    /**
     * AgentModeRail.
     * 
     * @since 0.1.7
     */
    public AgentModeRail() {
        this(DEFAULT_PLAN_ALLOWED_TOOLS);
    }

    /**
     * AgentModeRail.
     * 
     * @param allowedTools allowedTools
     * @since 0.1.7
     */
    public AgentModeRail(Set<String> allowedTools) {
        this.allowedTools =
            allowedTools == null || allowedTools.isEmpty() ? DEFAULT_PLAN_ALLOWED_TOOLS : Set.copyOf(allowedTools);
    }

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 85;
    }

    /**
     * init.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void init(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent)) {
            return;
        }
        owner = deepAgent;
        if (!tools.isEmpty()) {
            return;
        }
        String language = deepAgent.getWorkspace().getLanguage();
        SwitchModeTool switchModeTool = new SwitchModeTool(deepAgent);
        tools.add(new LocalFunction(card("switch_mode", deepAgent, language),
                inputs -> switchModeTool.switchMode(stringValue(inputs.get("mode")))));
        tools.add(new LocalFunction(card("enter_plan_mode", deepAgent, language),
                inputs -> enterPlanMode(deepAgent, switchModeTool, stringValue(inputs.get("conversation_id")))));
        tools.add(new LocalFunction(card("exit_plan_mode", deepAgent, language),
                inputs -> exitPlanMode(deepAgent, switchModeTool)));
        for (Tool tool : tools) {
            deepAgent.registerHarnessTool(tool);
        }
    }

    /**
     * uninit.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void uninit(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            for (Tool tool : tools) {
                deepAgent.unregisterHarnessTool(tool);
            }
        }
        tools.clear();
        unregisterOwnedTaskTool(agent);
        owner = null;
    }

    /**
     * enforceMode.
     * 
     * @param mode mode
     * @return the result
     * @since 0.1.7
     */
    public AgentMode enforceMode(AgentMode mode) {
        return mode != null ? mode : AgentMode.NORMAL;
    }

    /**
     * registeredToolNames.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> registeredToolNames() {
        return tools.stream().map(tool -> tool.getCard().getName()).toList();
    }

    /**
     * visibleToolNames.
     * 
     * @param mode mode
     * @param toolNames toolNames
     * @return the result
     * @since 0.1.7
     */
    public List<String> visibleToolNames(AgentMode mode, List<String> toolNames) {
        AgentMode effectiveMode = enforceMode(mode);
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        Set<String> hidden = effectiveMode == AgentMode.PLAN ? HIDDEN_IN_PLAN : HIDDEN_IN_NORMAL;
        LinkedHashSet<String> visible = new LinkedHashSet<>();
        for (String toolName : toolNames) {
            if (toolName != null && !hidden.contains(toolName)) {
                visible.add(toolName);
            }
        }
        return List.copyOf(visible);
    }

    /**
     * allowsToolInPlanMode.
     * 
     * @param toolName toolName
     * @return the result
     * @since 0.1.7
     */
    public boolean allowsToolInPlanMode(String toolName) {
        return toolName != null && allowedTools.contains(toolName) && !HIDDEN_IN_PLAN.contains(toolName);
    }

    /**
     * validateToolCall.
     * 
     * @param mode mode
     * @param toolName toolName
     * @param toolArgs toolArgs
     * @param planPath planPath
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput validateToolCall(AgentMode mode, String toolName, Map<String, Object> toolArgs, Path planPath) {
        if ("enter_plan_mode".equals(toolName)) {
            if (mode == AgentMode.PLAN) {
                return ToolOutput.builder().success(false).error("[AgentModeRail] already in plan mode").build();
            }
            return ToolOutput.builder().success(true).build();
        }
        if ("exit_plan_mode".equals(toolName)) {
            if (mode != AgentMode.PLAN) {
                return ToolOutput.builder().success(false).error("[AgentModeRail] not in plan mode").build();
            }
            return ToolOutput.builder().success(true).build();
        }
        if (mode != AgentMode.PLAN) {
            return ToolOutput.builder().success(true).build();
        }
        if (HIDDEN_IN_PLAN.contains(toolName)) {
            return ToolOutput.builder().success(false).error("[AgentModeRail] tool is hidden in plan mode: " + toolName)
                    .build();
        }
        if (!allowsToolInPlanMode(toolName)) {
            return ToolOutput.builder().success(false)
                    .error("[AgentModeRail] tool is not available in plan mode: " + toolName).build();
        }
        if (!allowsWriteTarget(toolName, extractFilePath(toolArgs), planPath)) {
            return ToolOutput.builder().success(false).error("[AgentModeRail] write/edit can only target the plan file")
                    .build();
        }
        return ToolOutput.builder().success(true).build();
    }

    /**
     * beforeModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (owner == null || owner.getCurrentMode() != AgentMode.PLAN) {
            removePlanModePromptSection();
            if (ctx.getInputs() instanceof ModelCallInputs inputs) {
                filterVisibleTools(inputs, AgentMode.NORMAL);
            }
            return;
        }
        String content = planModeInstructions(owner.getPlanFilePath());
        owner.getAgent().addPromptBuilderSection(PLAN_MODE_SECTION, content, PLAN_MODE_SECTION_PRIORITY);
        if (ctx.getInputs() instanceof ModelCallInputs inputs) {
            filterVisibleTools(inputs, AgentMode.PLAN);
            injectPlanModeMessage(inputs, content);
        }
    }

    /**
     * afterModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void afterModelCall(AgentCallbackContext ctx) {
        removePlanModePromptSection();
    }

    /**
     * beforeToolCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs) || owner == null) {
            return;
        }
        ToolOutput result = validateToolCall(owner.getCurrentMode(), inputs.getToolName(),
                normalizeArgs(inputs.getToolArgs()), owner.getPlanFilePath());
        if (result.isSuccess()) {
            return;
        }
        ctx.getExtra().put("_skip_tool", Boolean.TRUE);
        inputs.setToolResult(result);
        inputs.setToolMsg(ToolMessage.builder().content(result.getError())
                .toolCallId(inputs.getToolCall() != null ? inputs.getToolCall().getId() : "").build());
    }

    /**
     * afterToolCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs) || owner == null
                || Boolean.TRUE.equals(ctx.getExtra().get("_skip_tool"))) {
            return;
        }
        if ("enter_plan_mode".equals(inputs.getToolName())) {
            registerOwnedTaskTool(owner);
        } else if ("exit_plan_mode".equals(inputs.getToolName())) {
            unregisterOwnedTaskTool(owner);
        } else {
            // no-op
        }
    }

    /**
     * allowsWriteTarget.
     * 
     * @param toolName toolName
     * @param filePath filePath
     * @param planPath planPath
     * @return the result
     * @since 0.1.7
     */
    public boolean allowsWriteTarget(String toolName, String filePath, Path planPath) {
        if (toolName == null || !PLAN_WRITE_TOOLS.contains(toolName)) {
            return true;
        }
        if (filePath == null || filePath.isBlank() || planPath == null) {
            return false;
        }
        try {
            return Path.of(filePath).toAbsolutePath().normalize().equals(planPath.toAbsolutePath().normalize());
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * enterPlanMode.
     * 
     * @param agent agent
     * @param switchModeTool switchModeTool
     * @param conversationId conversationId
     * @return the result
     * @since 0.1.7
     */
    private static ToolOutput enterPlanMode(DeepAgent agent, SwitchModeTool switchModeTool, String conversationId) {
        Path planPath = agent.ensurePlanFile(conversationId);
        ToolOutput switched = switchModeTool.switchMode("plan");
        if (!switched.isSuccess()) {
            return switched;
        }
        AgentModeRail rail = findSelf(agent);
        if (rail != null) {
            rail.registerOwnedTaskTool(agent);
        }
        return ToolOutput.builder().success(true)
                .data(Map.of("previous_mode", ((Map<?, ?>) switched.getData()).get("previous_mode"), "current_mode",
                        ((Map<?, ?>) switched.getData()).get("current_mode"), "plan_file_path", planPath.toString()))
                .build();
    }

    /**
     * exitPlanMode.
     * 
     * @param agent agent
     * @param switchModeTool switchModeTool
     * @return the result
     * @since 0.1.7
     */
    private static ToolOutput exitPlanMode(DeepAgent agent, SwitchModeTool switchModeTool) {
        ToolOutput switched = switchModeTool.switchMode("normal");
        if (!switched.isSuccess()) {
            return switched;
        }
        AgentModeRail rail = findSelf(agent);
        if (rail != null) {
            rail.unregisterOwnedTaskTool(agent);
        }
        String planContent = "";
        Path planPath = agent.getPlanFilePath();
        if (planPath != null) {
            try {
                planContent = java.nio.file.Files.readString(planPath);
            } catch (IOException ignored) {
                planContent = "";
            }
        }
        return ToolOutput.builder().success(true)
                .data(Map.of("previous_mode", ((Map<?, ?>) switched.getData()).get("previous_mode"), "current_mode",
                        ((Map<?, ?>) switched.getData()).get("current_mode"), "plan_file_path",
                        planPath != null ? planPath.toString() : "", "plan_content", planContent))
                .build();
    }

    /**
     * card.
     * 
     * @param name name
     * @param agent agent
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private static ToolCard card(String name, DeepAgent agent, String language) {
        return ToolMetadataRegistry.buildToolCard(name, agent.getCard().getId() + "." + name, language);
    }

    /**
     * stringValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * registerOwnedTaskTool.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    private void registerOwnedTaskTool(DeepAgent agent) {
        if (ownedTaskTool != null || agent.getConfig().getSubagents() == null
                || agent.getConfig().getSubagents().isEmpty()) {
            return;
        }
        TaskTool taskTool = new TaskTool(agent);
        ToolCard metadataCard = ToolMetadataRegistry.buildToolCard("task_tool",
                agent.getCard().getId() + ".plan_mode.task_tool", agent.getWorkspace().getLanguage());
        ownedTaskTool = new LocalFunction(metadataCard,
                inputs -> taskTool.delegate(stringValue(inputs.get("subagent_type")),
                        stringValue(inputs.getOrDefault("task_description", inputs.get("task"))),
                        stringValue(inputs.get("parent_session_id"))));
        agent.registerHarnessTool(ownedTaskTool);
    }

    /**
     * unregisterOwnedTaskTool.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    private void unregisterOwnedTaskTool(Object agent) {
        if (ownedTaskTool == null) {
            return;
        }
        if (agent instanceof DeepAgent deepAgent) {
            deepAgent.unregisterHarnessTool(ownedTaskTool);
        }
        ownedTaskTool = null;
    }

    /**
     * ownsTaskTool.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean ownsTaskTool() {
        return ownedTaskTool != null;
    }

    /**
     * extractFilePath.
     * 
     * @param toolArgs toolArgs
     * @return the result
     * @since 0.1.7
     */
    private static String extractFilePath(Map<String, Object> toolArgs) {
        if (toolArgs == null) {
            return null;
        }
        Object path = toolArgs.get("file_path");
        if (path == null) {
            path = toolArgs.get("path");
        }
        return path == null ? null : String.valueOf(path);
    }

    /**
     * hasPlanModePromptSection.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasPlanModePromptSection() {
        return owner != null && owner.getAgent().getPromptBuilder().hasSection(PLAN_MODE_SECTION);
    }

    /**
     * planModeInstructions.
     * 
     * @param planPath planPath
     * @return the result
     * @since 0.1.7
     */
    public String planModeInstructions(Path planPath) {
        String path = planPath != null ? planPath.toAbsolutePath().normalize().toString() : "";
        return "Plan mode is active.\n" + "Only inspect information needed to produce or refine the plan.\n"
                + "Do not modify repository files. Write or edit only the active plan file.\n" + "Active plan file: "
                + path;
    }

    /**
     * removePlanModePromptSection.
     * 
     * @since 0.1.7
     */
    private void removePlanModePromptSection() {
        if (owner != null) {
            owner.getAgent().getPromptBuilder().removeSection(PLAN_MODE_SECTION);
        }
    }

    /**
     * injectPlanModeMessage.
     * 
     * @param inputs inputs
     * @param content content
     * @since 0.1.7
     */
    private void injectPlanModeMessage(ModelCallInputs inputs, String content) {
        List<Object> messages =
            inputs.getMessages() != null ? new ArrayList<>(inputs.getMessages()) : new ArrayList<>();
        for (Object message : messages) {
            if (message instanceof BaseMessage baseMessage && "system".equalsIgnoreCase(baseMessage.getRole())
                    && String.valueOf(baseMessage.getContent()).contains("Plan mode is active.")) {
                return;
            }
        }
        messages.add(0, new SystemMessage(content));
        inputs.setMessages(messages);
    }

    /**
     * filterVisibleTools.
     * 
     * @param inputs inputs
     * @param mode mode
     * @since 0.1.7
     */
    private void filterVisibleTools(ModelCallInputs inputs, AgentMode mode) {
        if (inputs.getTools() == null || inputs.getTools().isEmpty()) {
            return;
        }
        Set<String> hidden = mode == AgentMode.PLAN ? HIDDEN_IN_PLAN : HIDDEN_IN_NORMAL;
        List<ToolInfo> visible = new ArrayList<>();
        for (ToolInfo tool : inputs.getTools()) {
            if (tool != null && !hidden.contains(tool.getName())) {
                visible.add(tool);
            }
        }
        inputs.setTools(visible);
    }

    @SuppressWarnings("unchecked")
    /**
     * normalizeArgs.
     * 
     * @param args args
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> normalizeArgs(Object args) {
        if (args instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    /**
     * findSelf.
     * 
     * @param agent agent
     * @return the result
     * @since 0.1.7
     */
    private static AgentModeRail findSelf(DeepAgent agent) {
        if (agent.getConfig().getRails() == null) {
            return null;
        }
        for (Object rail : agent.getConfig().getRails()) {
            if (rail instanceof AgentModeRail modeRail) {
                return modeRail;
            }
        }
        return null;
    }
}
