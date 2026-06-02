/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.prompts.sections.AgentModeSection;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.tools.TaskTool;
import com.openjiuwen.harness.tools.agent_control.AgentModeTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Rail that enforces read-only plan mode constraints.
 *
 * <p>Always registered; activates conditionally based on
 * {@code DeepAgentState.planMode.mode == "plan"}.</p>
 *
 * <p>Mirrors Python's {@code AgentModeRail} in
 * {@code openjiuwen.harness.rails.agent_mode_rail}.</p>
 */
public class AgentModeRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(AgentModeRail.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> TODO_TOOL_NAMES = Set.of(
            "todo_create", "todo_list", "todo_modify"
    );
    private static final Set<String> SESSION_TOOL_NAMES = Set.of(
            "sessions_list", "sessions_cancel", "sessions_spawn"
    );
    private static final Set<String> HIDDEN_IN_PLAN = union(TODO_TOOL_NAMES, SESSION_TOOL_NAMES);
    private static final Set<String> HIDDEN_IN_NORMAL = Set.of("enter_plan_mode", "exit_plan_mode");
    private static final Set<String> PLAN_FILE_WRITE_TOOLS = Set.of("write_file", "edit_file");

    /** Default tools allowed in plan mode. */
    public static final String[] DEFAULT_PLAN_MODE_ALLOWED_TOOLS = {
            "switch_mode", "enter_plan_mode", "exit_plan_mode",
            "ask_user", "task_tool", "read_file", "grep",
            "list_files", "glob", "bash", "write_file", "edit_file"
    };

    private final Set<String> allowedTools;
    private final List<Tool> modeTools = new ArrayList<>();
    private final List<Tool> taskTools = new ArrayList<>();
    private final Set<String> ownedTaskToolNames = new LinkedHashSet<>();

    private DeepAgent agent;
    private Object systemPromptBuilder;
    private boolean ownsTaskTool;

    public AgentModeRail() {
        this(List.of(DEFAULT_PLAN_MODE_ALLOWED_TOOLS));
    }

    public AgentModeRail(Collection<String> allowedTools) {
        this.allowedTools = new LinkedHashSet<>(
                allowedTools != null ? allowedTools : List.of(DEFAULT_PLAN_MODE_ALLOWED_TOOLS)
        );
        setPriority(85);
    }

    @Override
    public void init(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent)) {
            return;
        }
        this.agent = deepAgent;
        this.systemPromptBuilder = deepAgent.getSystemPromptBuilder();
        registerModeTools(deepAgent);
    }

    @Override
    public void uninit(Object agent) {
        if (systemPromptBuilder != null) {
            removePromptSection(SectionName.MODE_INSTRUCTIONS);
        }
        if (agent instanceof DeepAgent deepAgent) {
            unregisterModeTools(deepAgent);
            unregisterTaskTool(deepAgent);
        }
        this.agent = null;
        this.systemPromptBuilder = null;
    }

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (ctx == null || agent == null) {
            return;
        }
        ModelCallInputs inputs = ctx.getInputs() instanceof ModelCallInputs typed ? typed : null;
        if (!isPlanMode(ctx.getSession())) {
            removePromptSection(SectionName.MODE_INSTRUCTIONS);
            filterVisibleTools(inputs != null ? inputs.getTools() : null, HIDDEN_IN_NORMAL);
            syncTaskToolVisibility(inputs, false);
            return;
        }

        Path planPath = agent.getPlanFilePath(ctx.getSession());
        String language = language();
        addPromptSection(AgentModeSection.buildPlanModeSection(
                language,
                planPath != null ? planPath.toString() : "",
                planPath != null && java.nio.file.Files.exists(planPath),
                planPath != null
        ));
        removePromptSection(SectionName.TODO);
        removePromptSection(SectionName.SESSION_TOOLS);
        filterVisibleTools(inputs != null ? inputs.getTools() : null, HIDDEN_IN_PLAN);
        syncTaskToolVisibility(inputs, true);
    }

    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (ctx == null || agent == null) {
            return;
        }
        ToolCallInputs inputs = ctx.getInputs() instanceof ToolCallInputs typed ? typed : null;
        String toolName = resolveToolName(inputs);

        if ("enter_plan_mode".equals(toolName)) {
            handleEnter(ctx, inputs);
            return;
        }
        if ("exit_plan_mode".equals(toolName)) {
            handleExit(ctx, inputs);
            return;
        }

        if (!isPlanMode(ctx.getSession()) || ctx.getExtra().get("_skip_tool") != null) {
            return;
        }

        if (HIDDEN_IN_PLAN.contains(toolName)) {
            rejectTool(inputs, ctx, languageIsCn()
                    ? "[AgentModeRail] plan mode 下已隐藏该工具。"
                    : "[AgentModeRail] Tool '" + toolName + "' is hidden in plan mode.");
            return;
        }

        if (!allowedTools.isEmpty() && !allowedTools.contains(toolName)) {
            rejectTool(inputs, ctx, languageIsCn()
                    ? "[AgentModeRail] 该工具在 plan mode 中不可用。"
                    : "[AgentModeRail] Tool '" + toolName + "' is not available in plan mode.");
            return;
        }

        if (PLAN_FILE_WRITE_TOOLS.contains(toolName)) {
            String filePath = extractFilePath(inputs);
            Path planPath = agent.getPlanFilePath(ctx.getSession());
            String planPathStr = planPath != null ? planPath.toString() : "";
            if (!isPlanFile(filePath, planPathStr)) {
                rejectTool(inputs, ctx, languageIsCn()
                        ? "[AgentModeRail] 该工具只能写入 plan 文件。"
                        : "[AgentModeRail] '" + toolName + "' can only target the plan file (" + planPathStr + ").");
            }
        }
    }

    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        if (ctx == null || agent == null) {
            return;
        }
        ToolCallInputs inputs = ctx.getInputs() instanceof ToolCallInputs typed ? typed : null;
        if (inputs == null || ctx.getExtra().get("_skip_tool") != null) {
            return;
        }
        String toolName = resolveToolName(inputs);
        if ("enter_plan_mode".equals(toolName)) {
            registerTaskTool(agent);
        } else if ("exit_plan_mode".equals(toolName)) {
            unregisterTaskTool(agent);
        }
    }

    private void handleEnter(AgentCallbackContext ctx, ToolCallInputs inputs) {
        if (isPlanMode(ctx.getSession())) {
            return;
        }
        rejectTool(inputs, ctx, languageIsCn()
                ? "[AgentModeRail] enter_plan_mode 只能在 plan mode 下调用。请先调用 switch_mode。"
                : "[AgentModeRail] enter_plan_mode can only be called in plan mode. Use the switch_mode tool to switch to plan mode.");
    }

    private void handleExit(AgentCallbackContext ctx, ToolCallInputs inputs) {
        if (isPlanMode(ctx.getSession())) {
            return;
        }
        rejectTool(inputs, ctx, languageIsCn()
                ? "[AgentModeRail] exit_plan_mode 只能在 plan mode 下调用。"
                : "[AgentModeRail] exit_plan_mode can only be called in plan mode.");
    }

    private void rejectTool(ToolCallInputs inputs, AgentCallbackContext ctx, String errorMsg) {
        String toolCallId = "";
        ToolCall toolCall = inputs != null ? inputs.getToolCall() : null;
        if (toolCall != null && toolCall.getId() != null) {
            toolCallId = toolCall.getId();
        }
        ctx.getExtra().put("_skip_tool", true);
        if (inputs != null) {
            inputs.setToolResult(Map.of("error", errorMsg));
            inputs.setToolMsg(new ToolMessage(errorMsg, toolCallId));
        }
    }

    private void syncTaskToolVisibility(ModelCallInputs inputs, boolean inPlanMode) {
        if (inputs == null || inputs.getTools() == null) {
            return;
        }
        List<ToolInfo> tools = inputs.getTools();
        if (ownsTaskTool && inPlanMode) {
            Set<String> existing = new LinkedHashSet<>();
            for (ToolInfo tool : tools) {
                if (tool != null && tool.getName() != null) {
                    existing.add(tool.getName());
                }
            }
            for (Tool tool : taskTools) {
                ToolCard card = tool.getCard();
                if (card != null && card.getName() != null && !existing.contains(card.getName())) {
                    tools.add(card.toolInfo());
                }
            }
            return;
        }
        if (!ownedTaskToolNames.isEmpty()) {
            tools.removeIf(tool -> ownedTaskToolNames.contains(readToolName(tool)));
            if (!ownsTaskTool) {
                ownedTaskToolNames.clear();
            }
        }
    }

    private void registerModeTools(DeepAgent deepAgent) {
        if (deepAgent == null) {
            return;
        }
        if (!modeTools.isEmpty()) {
            return;
        }
        AbilityManager abilityManager = deepAgent.getAbilityManager();
        String tag = readStringField(deepAgent.getCard(), "id");
        modeTools.add(new AgentModeTool(
                "switch_mode",
                "Switch agent mode.",
                (inputs, kwargs) -> new AgentModeTools.SwitchModeTool(deepAgent, language())
                        .invoke(inputs, sessionFrom(kwargs))
        ));
        modeTools.add(new AgentModeTool(
                "enter_plan_mode",
                "Enter plan mode.",
                (inputs, kwargs) -> new AgentModeTools.EnterPlanModeTool(deepAgent, language())
                        .invoke(inputs, sessionFrom(kwargs))
        ));
        modeTools.add(new AgentModeTool(
                "exit_plan_mode",
                "Exit plan mode.",
                (inputs, kwargs) -> new AgentModeTools.ExitPlanModeTool(deepAgent, language())
                        .invoke(inputs, sessionFrom(kwargs))
        ));

        for (Tool tool : modeTools) {
            if (abilityManager.get(tool.getCard().getName()) == null) {
                abilityManager.add(tool.getCard());
            }
            if (Runner.resourceMgr().getTool(tool.getCard().getId(), tag, TagMatchStrategy.ALL) == null) {
                Runner.resourceMgr().addTool(tool, tag);
            }
        }
    }

    private void unregisterModeTools(DeepAgent deepAgent) {
        if (deepAgent == null) {
            return;
        }
        String tag = readStringField(deepAgent.getCard(), "id");
        AbilityManager abilityManager = deepAgent.getAbilityManager();
        for (Tool tool : modeTools) {
            if (tool.getCard() != null) {
                abilityManager.remove(tool.getCard().getName());
                Runner.resourceMgr().removeTool(tool.getCard().getId(), tag, TagMatchStrategy.ALL, true);
            }
        }
        modeTools.clear();
    }

    private void registerTaskTool(DeepAgent deepAgent) {
        if (deepAgent == null || ownsTaskTool) {
            return;
        }
        if (!(deepAgent.getConfig() instanceof DeepAgentConfig config) || config.getSubagents().isEmpty()) {
            return;
        }
        if (deepAgent.getAbilityManager().get("task_tool") != null) {
            return;
        }

        String availableAgents = SubagentRail.buildAvailableAgentsDescription(config.getSubagents());
        List<TaskTool> created = TaskTool.createTaskTool(deepAgent, availableAgents, language());
        String tag = readStringField(deepAgent.getCard(), "id");
        for (TaskTool tool : created) {
            taskTools.add(tool);
            ownedTaskToolNames.add(tool.getCard().getName());
            Runner.resourceMgr().addTool(tool, tag);
            deepAgent.getAbilityManager().add(tool.getCard());
        }
        ownsTaskTool = !taskTools.isEmpty();
    }

    private void unregisterTaskTool(DeepAgent deepAgent) {
        if (deepAgent == null || taskTools.isEmpty()) {
            ownsTaskTool = false;
            return;
        }
        String tag = readStringField(deepAgent.getCard(), "id");
        for (Tool tool : taskTools) {
            if (tool.getCard() != null) {
                deepAgent.getAbilityManager().remove(tool.getCard().getName());
                Runner.resourceMgr().removeTool(tool.getCard().getId(), tag, TagMatchStrategy.ALL, true);
            }
        }
        taskTools.clear();
        ownsTaskTool = false;
    }

    private boolean isPlanMode(Session session) {
        return Objects.equals("plan", agent.loadState(session).getPlanMode().getMode());
    }

    private boolean languageIsCn() {
        return !"en".equalsIgnoreCase(language());
    }

    private String language() {
        Object value = invokeNoArg(systemPromptBuilder, "getLanguage");
        return "en".equalsIgnoreCase(value != null ? String.valueOf(value) : "") ? "en" : "cn";
    }

    private void addPromptSection(Object section) {
        invokeOneArg(systemPromptBuilder, "addSection", section);
    }

    private void removePromptSection(String sectionName) {
        invokeOneArg(systemPromptBuilder, "removeSection", sectionName);
    }

    private static String resolveToolName(ToolCallInputs inputs) {
        if (inputs == null) {
            return "";
        }
        String toolName = inputs.getToolName();
        if (toolName != null && !toolName.isBlank()) {
            return toolName.trim();
        }
        ToolCall toolCall = inputs.getToolCall();
        return toolCall != null && toolCall.getName() != null ? toolCall.getName() : "";
    }

    private static void filterVisibleTools(List<ToolInfo> tools, Set<String> hiddenNames) {
        if (tools == null || hiddenNames == null || hiddenNames.isEmpty()) {
            return;
        }
        tools.removeIf(tool -> hiddenNames.contains(readToolName(tool)));
    }

    private static String readToolName(Object tool) {
        if (tool instanceof ToolInfo typed) {
            return typed.getName();
        }
        Object value = invokeNoArg(tool, "getName");
        if (value != null) {
            return String.valueOf(value);
        }
        value = readField(tool, "name");
        return value != null ? String.valueOf(value) : "";
    }

    private static String extractFilePath(ToolCallInputs inputs) {
        if (inputs == null) {
            return "";
        }
        Object args = inputs.getToolArgs();
        if (args instanceof String text) {
            try {
                args = OBJECT_MAPPER.readValue(text, new TypeReference<Map<String, Object>>() { });
            } catch (Exception ignored) {
                return "";
            }
        }
        if (args instanceof Map<?, ?> map) {
            Object value = map.get("file_path");
            return value != null ? String.valueOf(value) : "";
        }
        return "";
    }

    static boolean isPlanFile(String filePath, String planPath) {
        if (filePath == null || filePath.isBlank() || planPath == null || planPath.isBlank()) {
            return false;
        }
        try {
            return Path.of(filePath).toAbsolutePath().normalize()
                    .equals(Path.of(planPath).toAbsolutePath().normalize());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Session sessionFrom(Map<String, Object> kwargs) {
        Object session = kwargs != null ? kwargs.get("session") : null;
        return session instanceof Session typed ? typed : null;
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(left);
        result.addAll(right);
        return Set.copyOf(result);
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void invokeOneArg(Object target, String methodName, Object arg) {
        if (target == null) {
            return;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameterType = method.getParameterTypes()[0];
            if (arg == null || parameterType.isInstance(arg) || isCompatible(parameterType, arg)) {
                try {
                    method.setAccessible(true);
                    method.invoke(target, arg);
                    return;
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Failed to invoke '" + methodName + "'", e);
                }
            }
        }
    }

    private static boolean isCompatible(Class<?> parameterType, Object arg) {
        if (arg == null) {
            return !parameterType.isPrimitive();
        }
        if (parameterType == String.class) {
            return arg instanceof String;
        }
        return false;
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
            }
        }
        return null;
    }

    private static String readStringField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        return value != null ? String.valueOf(value) : null;
    }

    private static final class AgentModeTool extends Tool {
        private final BiFunction<Map<String, Object>, Map<String, Object>, Object> invoker;

        private AgentModeTool(String name, String description,
                              BiFunction<Map<String, Object>, Map<String, Object>, Object> invoker) {
            super(ToolCard.builder()
                    .id(name)
                    .name(name)
                    .description(description)
                    .build());
            this.invoker = invoker;
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return invoker.apply(inputs != null ? inputs : Map.of(), kwargs != null ? kwargs : Map.of());
        }

        @Override
        public java.util.Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.of(invoke(inputs, kwargs)).iterator();
        }
    }
}
