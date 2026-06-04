/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;
import com.openjiuwen.harness.rails.AgentModeRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.interrupt.AskUserRail;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import com.openjiuwen.harness.tools.AskUserTool;
import com.openjiuwen.harness.tools.TodoCreateTool;
import com.openjiuwen.harness.tools.TodoGetTool;
import com.openjiuwen.harness.tools.TodoListTool;
import com.openjiuwen.harness.tools.TodoModifyTool;
import com.openjiuwen.harness.tools.agent_control.AgentModeTools;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Code agent configuration and factory.
 * <p>
 * Mirrors Python's {@code code_agent} in
 * {@code openjiuwen.harness.subagents.code_agent}.
 */
public final class CodeAgent {

    private CodeAgent() {
    }

    public static final String FACTORY_NAME = "code_agent";

    private static final String SYSTEM_PROMPT_CN =
            "你是一个 AI 编程助手，规则：能用工具就用工具（读/写/编辑/grep/list/bash/code），不要猜文件内容；"
            + "变更要小、可回滚；先澄清数据结构与接口，再动代码；输出给出测试/验证步骤。";

    private static final String SYSTEM_PROMPT_EN =
            "You are an AI Coding Agent. "
            + "Rules: Use tools whenever possible (read/write/edit/grep/list/bash/code), don't guess file contents;"
            + "make small, reversible changes; clarify data structures and interfaces before modifying code; "
            + "provide testing/verification steps in your output.";

    private static final String DESCRIPTION_CN = "资深软件工程师与代码代理。擅长把任务落到可运行的代码与可验证的结果。";
    private static final String DESCRIPTION_EN = "Senior software engineer and coding agent. Excels at translating tasks into runnable code and verifiable results.";

    public static String getSystemPrompt(String language) {
        return "en".equals(language) ? SYSTEM_PROMPT_EN : SYSTEM_PROMPT_CN;
    }

    public static String getDescription(String language) {
        return "en".equals(language) ? DESCRIPTION_EN : DESCRIPTION_CN;
    }

    public static DeepAgentConfig buildCodeAgentConfig(Model model) {
        return buildCodeAgentConfig(model, null, null, null, null, 15, null, null, null);
    }

    public static DeepAgentConfig buildCodeAgentConfig(
            Model model,
            AgentCard card,
            String systemPrompt,
            List<Tool> tools,
            List<AgentRail> rails,
            int maxIterations,
            Object workspace,
            SysOperation sysOperation,
            String language
    ) {
        String resolvedLanguage = resolveLanguage(language);
        AgentCard finalCard = card != null ? card : AgentCard.builder()
                .name(FACTORY_NAME)
                .description(getDescription(resolvedLanguage))
                .build();

        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(finalCard);
        config.setSystemPrompt(systemPrompt != null ? systemPrompt : getSystemPrompt(resolvedLanguage));
        config.setMaxIterations(maxIterations);
        config.setWorkspace(resolveWorkspace(workspace, resolvedLanguage));
        config.setSysOperation(sysOperation);
        config.setTools(toToolCards(tools));
        config.setRails(mergeRailsWithRequired(rails));
        config.setModel(model);
        assignModelConfig(config, model);
        return config;
    }

    public static DeepAgent createCodeAgent(Model model) {
        return createCodeAgent(model, null, null, null, null, null, false, 15, null, null, null);
    }

    public static DeepAgent createCodeAgent(Model model, SysOperation sysOperation) {
        return createCodeAgent(model, null, null, null, null, null, false, 15, null, sysOperation, null);
    }

    public static DeepAgent createCodeAgent(
            Model model,
            AgentCard card,
            String systemPrompt,
            List<Tool> tools,
            List<AgentRail> rails,
            List<DeepAgent> subagents,
            boolean enableTaskLoop,
            int maxIterations,
            Object workspace,
            SysOperation sysOperation,
            String language
    ) {
        String resolvedLanguage = resolveLanguage(language);
        DeepAgentConfig config = buildCodeAgentConfig(
                model,
                card,
                systemPrompt,
                tools,
                rails,
                maxIterations,
                workspace,
                sysOperation,
                resolvedLanguage
        );
        config.setSubagents(injectBuiltinPlanAgents(subagents, model, resolvedLanguage));

        DeepAgent agent = HarnessFactory.createDeepAgent(config);
        registerUserTools(agent, tools);
        registerCodeAgentBuiltins(agent, sysOperation, resolvedLanguage);
        return agent;
    }

    private static List<AgentRail> mergeRailsWithRequired(List<AgentRail> userRails) {
        List<AgentRail> merged = new ArrayList<>();
        if (userRails != null) {
            merged.addAll(userRails);
        }
        List<RequiredRail> required = List.of(
                new RequiredRail(SysOperationRail.class, () -> new SysOperationRail(true)),
                new RequiredRail(AgentModeRail.class, AgentModeRail::new),
                new RequiredRail(AskUserRail.class, AskUserRail::new),
                new RequiredRail(ConfirmInterruptRail.class, () -> new ConfirmInterruptRail(List.of("switch_mode")))
        );
        for (RequiredRail rail : required) {
            if (merged.stream().noneMatch(existing -> rail.type().isInstance(existing))) {
                merged.add(rail.factory().get());
            }
        }
        return merged;
    }

    private static List<DeepAgent> injectBuiltinPlanAgents(List<DeepAgent> subagents, Model model, String language) {
        List<DeepAgent> effective = new ArrayList<>();
        if (subagents != null) {
            effective.addAll(subagents);
        }
        if (!hasAgent(effective, ExploreAgent.FACTORY_NAME)) {
            effective.add(createBuiltinSubagent(
                    ExploreAgent.FACTORY_NAME,
                    ExploreAgent.getDescription(language),
                    ExploreAgent.getSystemPrompt(language),
                    model
            ));
        }
        if (!hasAgent(effective, PlanAgent.FACTORY_NAME)) {
            effective.add(createBuiltinSubagent(
                    PlanAgent.FACTORY_NAME,
                    PlanAgent.getDescription(language),
                    PlanAgent.getSystemPrompt(language),
                    model
            ));
        }
        return effective;
    }

    private static boolean hasAgent(List<DeepAgent> agents, String name) {
        for (DeepAgent agent : agents) {
            if (agent != null && agent.getCard() != null && name.equals(agent.getCard().getName())) {
                return true;
            }
        }
        return false;
    }

    private static DeepAgent createBuiltinSubagent(String name, String description, String systemPrompt, Model model) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(AgentCard.builder()
                .name(name)
                .description(description)
                .build());
        config.setSystemPrompt(systemPrompt);
        config.setMaxIterations(25);
        config.setModel(model);
        assignModelConfig(config, model);
        return HarnessFactory.createDeepAgent(config);
    }

    private static List<ToolCard> toToolCards(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream().map(Tool::getCard).toList();
    }

    private static void registerUserTools(DeepAgent agent, List<Tool> tools) {
        if (tools == null) {
            return;
        }
        for (Tool tool : tools) {
            registerTool(agent, tool);
        }
    }

    private static void registerCodeAgentBuiltins(DeepAgent agent, SysOperation sysOperation, String language) {
        List<Tool> builtins = new ArrayList<>();
        builtins.add(new TodoCreateTool(sysOperation));
        builtins.add(new TodoListTool(sysOperation));
        builtins.add(new TodoModifyTool(sysOperation));
        builtins.add(new TodoGetTool(sysOperation));
        builtins.add(new AskUserTool());
        builtins.add(new AgentModeTool("switch_mode", "Switch agent mode.",
                (inputs, kwargs) -> new AgentModeTools.SwitchModeTool(agent, language)
                        .invoke(inputs, sessionFrom(kwargs))));
        builtins.add(new AgentModeTool("enter_plan_mode", "Enter plan mode.",
                (inputs, kwargs) -> new AgentModeTools.EnterPlanModeTool(agent, language)
                        .invoke(inputs, sessionFrom(kwargs))));
        builtins.add(new AgentModeTool("exit_plan_mode", "Exit plan mode.",
                (inputs, kwargs) -> new AgentModeTools.ExitPlanModeTool(agent, language)
                        .invoke(inputs, sessionFrom(kwargs))));
        for (Tool tool : builtins) {
            registerTool(agent, tool);
        }
    }

    private static com.openjiuwen.core.session.Session sessionFrom(Map<String, Object> kwargs) {
        Object session = kwargs != null ? kwargs.get("session") : null;
        return session instanceof com.openjiuwen.core.session.Session typed ? typed : null;
    }

    private static void registerTool(DeepAgent agent, Tool tool) {
        if (agent == null || tool == null) {
            return;
        }
        if (tool.getCard() == null) {
            return;
        }
        if (Runner.resourceMgr().getTool(tool.getCard().getId()) == null) {
            Runner.resourceMgr().addTool(tool, agent.getCard().getId());
        }
        if (agent.getDelegate().getAbilityManager().get(tool.getCard().getName()) == null) {
            agent.getDelegate().getAbilityManager().add(tool.getCard());
        }
    }

    private static Workspace resolveWorkspace(Object workspace, String language) {
        if (workspace instanceof Workspace typed) {
            return typed;
        }
        if (workspace instanceof String path) {
            return new Workspace(path, language);
        }
        return null;
    }

    private static void assignModelConfig(DeepAgentConfig config, Model model) {
        if (config == null || model == null) {
            return;
        }
        ModelClientConfig clientConfig = readField(model, "modelClientConfig", ModelClientConfig.class);
        ModelRequestConfig requestConfig = readField(model, "modelConfig", ModelRequestConfig.class);
        config.setModelClientConfig(clientConfig);
        config.setModelRequestConfig(requestConfig);
    }

    private static String resolveLanguage(String language) {
        return "en".equalsIgnoreCase(language) ? "en" : "cn";
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String fieldName, Class<T> type) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(target);
                return type.isInstance(value) ? (T) value : null;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
            }
        }
        return null;
    }

    private record RequiredRail(Class<? extends AgentRail> type, Supplier<AgentRail> factory) {
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
