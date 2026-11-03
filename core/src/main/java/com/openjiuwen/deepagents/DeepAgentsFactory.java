/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.deepagents;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigBuilder;
import com.openjiuwen.harness.harness_config.HarnessConfigLoader;
import com.openjiuwen.harness.harness_config.ResolvedHarnessConfig;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.schema.config.DeepAgentConfigConverter;
import com.openjiuwen.harness.subagents.SubAgentConfig;
import com.openjiuwen.harness.task_loop.TaskLoopEventExecutor;
import com.openjiuwen.harness.task_loop.TaskLoopEventHandler;
import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level factory / module facade for DeepAgent construction.
 *
 * <p>Absorbs former {@code com.openjiuwen.harness.DeepAgentFactory} and
 * {@code com.openjiuwen.harness.HarnessPackage}. Low-level assembly stays in
 * {@link HarnessFactory}.
 */
public class DeepAgentsFactory {

    public static final String PYTHON_MODULE = "openjiuwen/harness/__init__.py";

    public DeepAgentsFactory() {
    }

    public DeepAgent createDeepAgent() {
        return HarnessFactory.createDeepAgent(DeepAgentConfig.builder().build());
    }

    public DeepAgent createDeepAgent(DeepAgentConfig config) {
        return HarnessFactory.createDeepAgent(
                config == null ? DeepAgentConfig.builder().build() : config);
    }

    public DeepAgent createDeepAgent(com.openjiuwen.harness.schema.DeepAgentConfig config) {
        return HarnessFactory.createDeepAgent(DeepAgentConfigConverter.toRuntime(config));
    }

    public DeepAgent createDeepAgent(Path configPath) {
        return createFromConfig(configPath);
    }

    public DeepAgent createDeepAgent(String configPath) {
        return createFromConfig(configPath);
    }

    public DeepAgent createDeepAgent(HarnessConfig harnessConfig) {
        if (harnessConfig == null) {
            return createDeepAgent();
        }
        String name = stringOrDefault(harnessConfig.getName(), "deep_agent");
        String systemPrompt = firstPromptSection(harnessConfig);
        DeepAgentConfig runtime = DeepAgentConfig.builder()
                .systemPrompt(systemPrompt == null ? "" : systemPrompt)
                .build();
        AgentCard card = new AgentCard(name, name, name);
        return HarnessFactory.createDeepAgent(card, runtime, null);
    }

    private static String firstPromptSection(HarnessConfig harnessConfig) {
        if (harnessConfig.getPrompts() == null || harnessConfig.getPrompts().getSections() == null) {
            return null;
        }
        for (HarnessConfig.SectionSchema section : harnessConfig.getPrompts().getSections()) {
            String content = contentAsString(section == null ? null : section.getContent());
            if (content != null && !content.isBlank()) {
                return content;
            }
        }
        return null;
    }

    private static DeepAgent createDeepAgentFromResolved(ResolvedHarnessConfig resolved) {
        com.openjiuwen.harness.schema.DeepAgentConfig built = HarnessConfigBuilder.build(
                resolved, null, Path.of(".").toAbsolutePath().normalize());
        DeepAgentConfig runtime = DeepAgentConfigConverter.toRuntime(built);
        if (resolved != null && resolved.getSystemPrompt() != null && !resolved.getSystemPrompt().isBlank()) {
            runtime.setSystemPrompt(resolved.getSystemPrompt());
        }
        AgentCard card = built.getCard();
        if (card == null && resolved != null && resolved.getConfig() != null) {
            String name = stringOrDefault(resolved.getConfig().getName(), null);
            if (name != null) {
                card = new AgentCard(name, name, name);
            }
        }
        return HarnessFactory.createDeepAgent(card, runtime, null);
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private static String contentAsString(Object content) {
        if (content == null) {
            return null;
        }
        if (content instanceof CharSequence text) {
            return text.toString();
        }
        return String.valueOf(content);
    }

    public DeepAgent createFromConfig(Object config) {
        if (config == null) {
            return createDeepAgent();
        }
        if (config instanceof DeepAgentConfig deepAgentConfig) {
            return createDeepAgent(deepAgentConfig);
        }
        if (config instanceof com.openjiuwen.harness.schema.DeepAgentConfig schemaConfig) {
            return createDeepAgent(schemaConfig);
        }
        if (config instanceof String path) {
            return createDeepAgentFromResolved(HarnessConfigLoader.load(Path.of(path)));
        }
        if (config instanceof Path path) {
            return createDeepAgentFromResolved(HarnessConfigLoader.load(path));
        }
        if (config instanceof HarnessConfig harnessConfig) {
            return createDeepAgent(harnessConfig);
        }
        throw new IllegalArgumentException("Unsupported deep agent config type: " + config.getClass().getName());
    }

    public static DeepAgent createDeepAgent(Object model) {
        return createDeepAgent(model, List.of(), Map.of());
    }

    public static DeepAgent createDeepAgent(
            Object model,
            List<Tool> tools,
            Map<String, com.openjiuwen.harness.schema.DeepAgentConfig.SubAgentConfig> subagents
    ) {
        com.openjiuwen.harness.schema.DeepAgentConfig legacy = new com.openjiuwen.harness.schema.DeepAgentConfig();
        legacy.setModel(model);
        legacy.setTools(normalizeTools(tools));
        legacy.setSubagents(injectGeneralPurposeSubagent(subagents));
        AgentCard card = new AgentCard("deep_agent", "deep_agent", "DeepAgent");
        legacy.setCard(card);
        DeepAgentConfig runtime = DeepAgentConfigConverter.toRuntime(legacy);
        injectGeneralPurposeRuntime(runtime);
        return HarnessFactory.createDeepAgent(card, runtime, null);
    }

    public static List<Tool> normalizeTools(List<Tool> tools) {
        return tools == null ? new ArrayList<>() : new ArrayList<>(tools);
    }

    public static Map<String, com.openjiuwen.harness.schema.DeepAgentConfig.SubAgentConfig> injectGeneralPurposeSubagent(
            Map<String, com.openjiuwen.harness.schema.DeepAgentConfig.SubAgentConfig> subagents
    ) {
        Map<String, com.openjiuwen.harness.schema.DeepAgentConfig.SubAgentConfig> result = new LinkedHashMap<>();
        if (subagents != null) {
            result.putAll(subagents);
        }
        result.putIfAbsent(
                DeepAgentConfig.GENERAL_PURPOSE_AGENT_NAME,
                new com.openjiuwen.harness.schema.DeepAgentConfig.SubAgentConfig(
                        DeepAgentConfig.GENERAL_PURPOSE_AGENT_NAME,
                        "General purpose subagent",
                        "You are a general-purpose subagent."
                )
        );
        return result;
    }

    public static boolean isDisabledFreeSearchTool(Tool tool) {
        return tool != null
                && tool.getCard() != null
                && "free_search".equals(tool.getCard().getName());
    }

    public static List<Object> exports() {
        return List.of(
                DeepAgent.class,
                TaskLoopEventHandler.class,
                TaskLoopEventExecutor.class,
                DeepAgentConfig.class,
                "AudioModelConfig",
                "VisionModelConfig",
                "create_deep_agent",
                Workspace.class
        );
    }

    public static Object getAttribute(String name) {
        return switch (name) {
            case "DeepAgent" -> DeepAgent.class;
            case "TaskLoopEventHandler" -> TaskLoopEventHandler.class;
            case "TaskLoopEventExecutor" -> TaskLoopEventExecutor.class;
            case "DeepAgentConfig" -> DeepAgentConfig.class;
            case "AudioModelConfig" -> "AudioModelConfig";
            case "VisionModelConfig" -> "VisionModelConfig";
            case "create_deep_agent" -> "create_deep_agent";
            case "Workspace" -> Workspace.class;
            default -> throw new IllegalArgumentException(
                    "module 'openjiuwen.harness' has no attribute '" + name + "'"
            );
        };
    }

    private static void injectGeneralPurposeRuntime(DeepAgentConfig runtime) {
        if (runtime.getSubagents() == null) {
            runtime.setSubagents(new ArrayList<>());
        }
        boolean present = runtime.getSubagents().stream().anyMatch(DeepAgentsFactory::isGeneralPurpose);
        if (!present) {
            runtime.getSubagents().add(SubAgentConfig.builder()
                    .agentCard(AgentCard.builder()
                            .name(DeepAgentConfig.GENERAL_PURPOSE_AGENT_NAME)
                            .description("General purpose subagent")
                            .build())
                    .systemPrompt("You are a general-purpose subagent.")
                    .build());
        }
        runtime.setGeneralPurposeAgentEnabled(true);
    }

    private static boolean isGeneralPurpose(Object item) {
        if (item instanceof SubAgentConfig spec) {
            return spec.getAgentCard() != null && DeepAgentConfig.GENERAL_PURPOSE_AGENT_NAME.equals(spec.getAgentCard().getName());
        }
        if (item instanceof DeepAgent agent) {
            return agent.getCard() != null && DeepAgentConfig.GENERAL_PURPOSE_AGENT_NAME.equals(agent.getCard().getName());
        }
        return false;
    }
}
