/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent.llm_agent;

import com.openjiuwen.core.application.llm.LlmAgent;
import com.openjiuwen.core.application.schema.LlmAgentConfig;
import com.openjiuwen.core.application.schema.PluginSchema;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * LLMAgent-compatible fixture backed by the Java application {@link LlmAgent}.
 * <p>
 * Mirrors Python's {@code MockLLMAgent}, {@code create_llm_agent_config}, and
 * {@code create_llm_agent} in
 * {@code tests.unit_tests.agent.llm_agent.mock_llm_agent}.
 * <p>
 * Java adaptation note: the production Java {@link LlmAgent} already contains
 * the legacy-to-application config bridge used by Python's mock adapter, so
 * this fixture keeps the same public helper surface while delegating invocation
 * and streaming to the underlying Java agent.
 */
public class MockLlmAgent extends LlmAgent {

    private final LlmAgentConfig agentConfig;
    private final List<Tool> tools = new ArrayList<>();
    private final List<Workflow> workflows = new ArrayList<>();

    public MockLlmAgent(LlmAgentConfig agentConfig) {
        super(Objects.requireNonNull(agentConfig, "agentConfig"));
        this.agentConfig = agentConfig;
    }

    public LlmAgentConfig config() {
        return agentConfig;
    }

    public List<Tool> tools() {
        return List.copyOf(tools);
    }

    public List<Workflow> workflows() {
        return List.copyOf(workflows);
    }

    @Override
    public ContextEngine getContextEngine() {
        return super.getContextEngine();
    }

    @Override
    public void addPrompt(List<Map<String, String>> promptTemplate) {
        if (promptTemplate == null || promptTemplate.isEmpty()) {
            return;
        }
        List<Map<String, String>> merged = agentConfig.getPromptTemplate() == null
            ? new ArrayList<>()
            : new ArrayList<>(agentConfig.getPromptTemplate());
        for (Map<String, String> entry : promptTemplate) {
            merged.add(new LinkedHashMap<>(entry));
        }
        setPromptTemplate(merged);
    }

    @Override
    public void setPromptTemplate(List<Map<String, String>> promptTemplate) {
        List<Map<String, String>> copied = new ArrayList<>();
        if (promptTemplate != null) {
            for (Map<String, String> entry : promptTemplate) {
                copied.add(new LinkedHashMap<>(entry));
            }
        }
        super.setPromptTemplate(copied);
    }

    public void addTools(List<Tool> newTools) {
        if (newTools == null) {
            return;
        }
        for (Tool tool : newTools) {
            if (tool == null || tool.getCard() == null) {
                continue;
            }
            ToolCard card = tool.getCard();
            if (tools.stream().noneMatch(existing -> Objects.equals(existing.getCard().getName(), card.getName()))) {
                tools.add(tool);
            }
        }
        super.addTools(newTools);
    }

    public void addWorkflows(List<Workflow> newWorkflows) {
        if (newWorkflows == null) {
            return;
        }
        for (Workflow workflow : newWorkflows) {
            if (workflow == null || workflow.getCard() == null) {
                continue;
            }
            WorkflowCard card = workflow.getCard();
            String workflowKey = WorkflowUtils.generateWorkflowKey(card.getId(), card.getVersion());
            if (workflows.stream().noneMatch(existing -> Objects.equals(
                WorkflowUtils.generateWorkflowKey(existing.getCard().getId(), existing.getCard().getVersion()),
                workflowKey))) {
                workflows.add(workflow);
            }
        }
        super.addWorkflows(newWorkflows);
    }

    @Override
    public void removeWorkflows(Collection<String[]> workflowIds) {
        if (workflowIds == null || workflowIds.isEmpty()) {
            return;
        }
        for (String[] workflowId : workflowIds) {
            if (workflowId == null || workflowId.length < 2) {
                continue;
            }
            String workflowKey = WorkflowUtils.generateWorkflowKey(workflowId[0], workflowId[1]);
            workflows.removeIf(workflow -> Objects.equals(
                WorkflowUtils.generateWorkflowKey(workflow.getCard().getId(), workflow.getCard().getVersion()),
                workflowKey));
        }
        super.removeWorkflows(workflowIds);
    }

    public void bindWorkflows(List<Workflow> newWorkflows) {
        addWorkflows(newWorkflows);
    }

    public void addPlugins(List<PluginSchema> plugins) {
        if (plugins == null) {
            return;
        }
        for (PluginSchema plugin : plugins) {
            boolean exists = agentConfig.getPlugins().stream()
                .anyMatch(existing -> Objects.equals(existing.getName(), plugin.getName()));
            if (!exists) {
                agentConfig.getPlugins().add(plugin);
            }
        }
    }

    @Override
    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        return super.stream(inputs, session, streamModes);
    }

    public void clearSession() {
        clearSession("default_session");
    }

    public void clearSession(String sessionId) {
        releaseSession(sessionId);
        getContextEngine().clearContext(null, sessionId);
    }

    public static LlmAgentConfig createLlmAgentConfig(
            String agentId,
            String agentVersion,
            String description,
            List<WorkflowSchema> workflows,
            List<PluginSchema> plugins,
            ModelConfig model,
            List<Map<String, String>> promptTemplate,
            List<String> tools) {
        return LlmAgent.createLlmAgentConfig(
            agentId,
            agentVersion,
            description,
            workflows,
            plugins,
            model,
            promptTemplate,
            tools
        );
    }

    public static MockLlmAgent createLlmAgent(
            LlmAgentConfig agentConfig,
            List<Workflow> workflows,
            List<Tool> tools) {
        MockLlmAgent agent = new MockLlmAgent(agentConfig);
        agent.addWorkflows(workflows);
        agent.addTools(tools);
        return agent;
    }
}
