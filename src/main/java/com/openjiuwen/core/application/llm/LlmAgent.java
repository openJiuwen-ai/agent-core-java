/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm;

import com.openjiuwen.core.application.llm_agent.LLMAgent;
import com.openjiuwen.core.application.schema.LlmAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.singleagent.legacy.config.AgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Backward-compatible facade for the 0.1.12 LLM agent class.
 *
 * <p>Mirrors Python's {@code LLMAgent} in
 * {@code openjiuwen/core/application/llm_agent/llm_agent.py}.</p>
 */
public class LlmAgent extends LLMAgent {
    private final AbilityManager abilityManager = new AbilityManager();
    private final AgentCard card;
    private final LlmAgentConfig applicationAgentConfig;

    public LlmAgent(LegacyReActAgentConfig agentConfig) {
        super(agentConfig);
        this.applicationAgentConfig = toApplicationConfig(agentConfig);
        this.card = toAgentCard(agentConfig);
        this.abilityManager.setContextEngine(getContextEngine());
    }

    public LlmAgent(LlmAgentConfig agentConfig) {
        super(toLegacyConfig(agentConfig));
        this.applicationAgentConfig = Objects.requireNonNull(agentConfig, "agentConfig");
        this.card = toAgentCard(agentConfig);
        this.abilityManager.setContextEngine(getContextEngine());
    }

    @Override
    public LlmAgentConfig getAgentConfig() {
        return applicationAgentConfig;
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public AbilityManager get_ability_manager() {
        return abilityManager;
    }

    public AgentCard getCard() {
        return card;
    }

    @Override
    public com.openjiuwen.core.context.ContextEngine getContextEngine() {
        return (com.openjiuwen.core.context.ContextEngine) super.getContextEngine();
    }

    @Override
    protected com.openjiuwen.core.context.ContextEngine createContextEngine() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setMaxContextMessageNum(readReservedMaxChatRounds(getAgentConfig()) * 2);
        return new com.openjiuwen.core.context.ContextEngine(config);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addPrompt(List promptTemplate) {
        List<Map<String, Object>> objectPrompt = copyPromptTemplateObjects(promptTemplate);
        super.addPrompt(objectPrompt);
        applicationAgentConfig.getPromptTemplate().addAll(copyPromptTemplateStrings(promptTemplate));
    }

    public static LlmAgentConfig createLlmAgentConfig(String agentId,
                                                      String agentVersion,
                                                      String description,
                                                      List<?> workflows,
                                                      List<?> plugins,
                                                      ModelConfig model,
                                                      List<? extends Map<String, ?>> promptTemplate) {
        return createLlmAgentConfig(
                agentId,
                agentVersion,
                description,
                workflows,
                plugins,
                model,
                promptTemplate,
                null
        );
    }

    public static LlmAgentConfig createLlmAgentConfig(String agentId,
                                                      String agentVersion,
                                                      String description,
                                                      List<?> workflows,
                                                      List<?> plugins,
                                                      ModelConfig model,
                                                      List<? extends Map<String, ?>> promptTemplate,
                                                      List<String> tools) {
        LlmAgentConfig config = new LlmAgentConfig();
        config.setId(agentId);
        config.setVersion(agentVersion);
        config.setDescription(description);
        config.setWorkflows(copyWorkflowSchemas(workflows));
        config.setPlugins(copyPluginSchemas(plugins));
        config.setModel(model);
        config.setPromptTemplate(copyPromptTemplateStrings(promptTemplate));
        config.setTools(tools == null ? List.of() : tools);
        return config;
    }

    public static LlmAgent createLlmAgent(LegacyReActAgentConfig agentConfig) {
        return createLlmAgent(agentConfig, null, null);
    }

    public static LlmAgent createLlmAgent(LegacyReActAgentConfig agentConfig,
                                          List<Workflow> workflows,
                                          List<Tool> tools) {
        LlmAgent agent = new LlmAgent(agentConfig);
        agent.addWorkflows(workflows);
        agent.addTools(tools == null ? List.of() : tools);
        return agent;
    }

    public static LlmAgent createLlmAgent(LlmAgentConfig agentConfig) {
        return createLlmAgent(agentConfig, null, null);
    }

    public static LlmAgent createLlmAgent(LlmAgentConfig agentConfig,
                                          List<Workflow> workflows,
                                          List<Tool> tools) {
        return createLlmAgent(toLegacyConfig(agentConfig), workflows, tools);
    }

    private static LegacyReActAgentConfig toLegacyConfig(LlmAgentConfig source) {
        Objects.requireNonNull(source, "agentConfig");
        LegacyReActAgentConfig config = new LegacyReActAgentConfig();
        config.setId(source.getId());
        config.setVersion(source.getVersion());
        config.setDescription(source.getDescription());
        config.setControllerType(source.getControllerType());
        config.setWorkflows(source.getWorkflows());
        config.setPlugins(source.getPlugins());
        config.setModel(source.getModel());
        config.setPromptTemplate(copyPromptTemplate(source.getPromptTemplate()));
        config.setTools(source.getTools());
        config.setMemoryScopeId(source.getMemoryScopeId());
        config.setAgentMemoryConfig(source.getAgentMemoryConfig());
        if (source.getConstrain() != null) {
            com.openjiuwen.core.singleagent.legacy.config.ConstrainConfig constrain =
                    new com.openjiuwen.core.singleagent.legacy.config.ConstrainConfig();
            constrain.setReservedMaxChatRounds(source.getConstrain().getReservedMaxChatRounds());
            config.setConstrain(constrain);
        }
        return config;
    }

    private static AgentCard toAgentCard(AgentConfig source) {
        Objects.requireNonNull(source, "agentConfig");
        return new AgentCard(valueOrEmpty(source.getId()), valueOrEmpty(source.getId()),
                valueOrEmpty(source.getDescription()));
    }

    private static AgentCard toAgentCard(LlmAgentConfig source) {
        Objects.requireNonNull(source, "agentConfig");
        return new AgentCard(valueOrEmpty(source.getId()), valueOrEmpty(source.getId()),
                valueOrEmpty(source.getDescription()));
    }

    private static LlmAgentConfig toApplicationConfig(LegacyReActAgentConfig source) {
        Objects.requireNonNull(source, "agentConfig");
        LlmAgentConfig target = new LlmAgentConfig();
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setDescription(source.getDescription());
        target.setControllerType(source.getControllerType());
        target.setModel(source.getModel());
        target.setPromptTemplateName(source.getPromptTemplateName());
        target.setPromptTemplate(copyPromptTemplateStrings(source.getPromptTemplate()));
        target.setTools(source.getTools());
        target.setMemoryScopeId(source.getMemoryScopeId());
        target.setAgentMemoryConfig(source.getAgentMemoryConfig());
        target.setWorkflows(copyWorkflowSchemas(source.getWorkflows()));
        return target;
    }

    private static List<WorkflowSchema> copyWorkflowSchemas(List<?> source) {
        List<WorkflowSchema> copy = new ArrayList<>();
        if (source != null) {
            for (Object item : source) {
                if (item instanceof WorkflowSchema workflowSchema) {
                    copy.add(workflowSchema);
                }
            }
        }
        return copy;
    }

    private static List<com.openjiuwen.core.application.schema.PluginSchema> copyPluginSchemas(List<?> source) {
        List<com.openjiuwen.core.application.schema.PluginSchema> copy = new ArrayList<>();
        if (source != null) {
            for (Object item : source) {
                if (item instanceof com.openjiuwen.core.application.schema.PluginSchema pluginSchema) {
                    copy.add(pluginSchema);
                }
            }
        }
        return copy;
    }

    private static int readReservedMaxChatRounds(Object config) {
        Object constrain = readProperty(config, "getConstrain");
        Object value = readProperty(constrain, "getReservedMaxChatRounds");
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 10;
    }

    private static Object readProperty(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return null;
        }
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static List<Map<String, Object>> copyPromptTemplate(List<? extends Map<String, ?>> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (source != null) {
            for (Map<String, ?> item : source) {
                Map<String, Object> prompt = new LinkedHashMap<>();
                if (item != null) {
                    prompt.putAll(item);
                }
                copy.add(prompt);
            }
        }
        return copy;
    }

    private static List<Map<String, Object>> copyPromptTemplateObjects(List<?> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (source != null) {
            for (Object item : source) {
                Map<String, Object> prompt = new LinkedHashMap<>();
                if (item instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        prompt.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                copy.add(prompt);
            }
        }
        return copy;
    }

    private static List<Map<String, String>> copyPromptTemplateStrings(List<?> source) {
        List<Map<String, String>> copy = new ArrayList<>();
        if (source != null) {
            for (Object item : source) {
                Map<String, String> prompt = new LinkedHashMap<>();
                if (item instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        prompt.put(String.valueOf(entry.getKey()),
                                entry.getValue() == null ? null : String.valueOf(entry.getValue()));
                    }
                }
                copy.add(prompt);
            }
        }
        return copy;
    }
}
