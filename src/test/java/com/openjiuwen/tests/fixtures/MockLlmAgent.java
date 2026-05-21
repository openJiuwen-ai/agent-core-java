/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.fixtures;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.legacy.BaseAgent;
import com.openjiuwen.core.singleagent.legacy.schema.PluginSchema;
import com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Mock LLMAgent-compatible adapter backed by ReActAgent.
 * <p>
 * Mirrors Python's {@code mock_llm_agent.py} in
 * {@code tests/unit_tests/agent/llm_agent/mock_llm_agent.py}.
 */
public class MockLlmAgent extends BaseAgent {

    private static final LoggerProtocol LOGGER = Loggers.AGENT;

    private final ReActAgent inner;

    public MockLlmAgent(com.openjiuwen.core.singleagent.legacy.config.ReActAgentConfig agentConfig) {
        super(agentConfig);
        ReActAgentConfig reactConfig = convertLegacyConfig(agentConfig);
        AgentCard card = AgentCard.builder()
                .id(agentConfig.getId())
                .name(agentConfig.getId())
                .description(agentConfig.getDescription())
                .build();
        this.inner = new ReActAgent(card);
        this.inner.configure(reactConfig);
    }

    private static ReActAgentConfig convertLegacyConfig(
            com.openjiuwen.core.singleagent.legacy.config.ReActAgentConfig legacy) {
        ReActAgentConfig config = ReActAgentConfig.builder().build();

        config.setPromptTemplate(new ArrayList<>(legacy.getPromptTemplate()));
        config.configureContextEngine(200, legacy.getConstrain() != null
                ? legacy.getConstrain().getReservedMaxChatRounds() : 10, false);

        if (legacy.getModel() != null) {
            String provider = legacy.getModel().getModelProvider() != null
                    ? legacy.getModel().getModelProvider() : "";
            String modelName = "";
            String apiKey = "";
            String apiBase = "";

            if (legacy.getModel().getModelInfo() != null) {
                modelName = legacy.getModel().getModelInfo().getModelName() != null
                        ? legacy.getModel().getModelInfo().getModelName() : "";
                apiKey = legacy.getModel().getModelInfo().getApiKey() != null
                        ? legacy.getModel().getModelInfo().getApiKey() : "";
                apiBase = legacy.getModel().getModelInfo().getApiBase() != null
                        ? legacy.getModel().getModelInfo().getApiBase() : "";
            }

            if (!provider.isEmpty() && !modelName.isEmpty()) {
                config.configureModelClient(provider, apiKey, apiBase, modelName, false);
            } else {
                config.setModelProvider(provider);
                config.setModelName(modelName);
                config.setApiKey(apiKey);
                config.setApiBase(apiBase);
            }
        }

        return config;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Session session) {
        return inner.invoke(inputs, session);
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Session session) {
        return inner.stream(inputs, session, List.of());
    }

    @Override
    public void addTools(List<Tool> newTools) {
        if (newTools == null || newTools.isEmpty()) return;
        for (Tool tool : newTools) {
            if (!agentConfig.getTools().contains(tool.getCard().getName())) {
                agentConfig.getTools().add(tool.getCard().getName());
            }
            boolean exists = tools.stream().anyMatch(t -> t.getCard().getName().equals(tool.getCard().getName()));
            if (!exists) {
                tools.add(tool);
            }
            Runner.resourceMgr().addTool(tool, agentConfig.getId());
            inner.getAbilityManager().add(tool.getCard());
        }
    }

    @Override
    public void addWorkflows(List<Workflow> newWorkflows) {
        if (newWorkflows == null || newWorkflows.isEmpty()) return;
        for (Workflow workflow : newWorkflows) {
            workflows.add(workflow);
            WorkflowCard card = workflow.getCard();
            String workflowKey = card.getId() + ":" + card.getVersion();

            boolean exists = agentConfig.getWorkflows().stream()
                    .anyMatch(w -> (w.getId() + ":" + w.getVersion()).equals(workflowKey));
            if (!exists) {
                agentConfig.getWorkflows().add(new WorkflowSchema(
                        card.getId(), card.getName(), card.getVersion(),
                        card.getDescription() != null ? card.getDescription() : "",
                        card.getInputParams()
                ));
            }
            Runner.resourceMgr().addWorkflow(card, () -> workflow, agentConfig.getId());
        }
    }

    public void removeWorkflows(List<WorkflowCard> toRemove) {
        for (WorkflowCard card : toRemove) {
            String workflowKey = card.getId() + ":" + card.getVersion();
            agentConfig.getWorkflows().removeIf(w ->
                    (w.getId() + ":" + w.getVersion()).equals(workflowKey));
            try {
                Runner.resourceMgr().removeWorkflow(workflowKey, agentConfig.getId(),
                        com.openjiuwen.core.runner.base.TagMatchStrategy.MATCH_EXACT, true);
            } catch (Exception e) {
                LOGGER.error("Failed to remove workflow from global resource_mgr: {}", e.getMessage());
            }
        }
    }

    public ReActAgent getInner() {
        return inner;
    }

    public static com.openjiuwen.core.singleagent.legacy.config.ReActAgentConfig createLlmAgentConfig(
            String agentId,
            String agentVersion,
            String description,
            List<WorkflowSchema> workflowSchemas,
            List<PluginSchema> plugins,
            ModelConfig model,
            List<Map<String, String>> promptTemplate,
            List<String> toolNames) {

        com.openjiuwen.core.singleagent.legacy.config.ReActAgentConfig config =
                new com.openjiuwen.core.singleagent.legacy.config.ReActAgentConfig();
        config.setId(agentId);
        config.setVersion(agentVersion);
        config.setDescription(description);
        if (workflowSchemas != null) config.setWorkflows(new ArrayList<>(workflowSchemas));
        if (plugins != null) config.setPlugins(new ArrayList<>(plugins));
        config.setModel(model);
        config.setPromptTemplate(promptTemplate != null ? new ArrayList<>(promptTemplate) : new ArrayList<>());
        config.setTools(toolNames != null ? new ArrayList<>(toolNames) : new ArrayList<>());
        return config;
    }

    public static MockLlmAgent createLlmAgent(
            com.openjiuwen.core.singleagent.legacy.config.ReActAgentConfig agentConfig,
            List<Workflow> workflows,
            List<Tool> tools) {
        MockLlmAgent agent = new MockLlmAgent(agentConfig);
        if (workflows != null) agent.addWorkflows(workflows);
        if (tools != null) agent.addTools(tools);
        return agent;
    }
}
