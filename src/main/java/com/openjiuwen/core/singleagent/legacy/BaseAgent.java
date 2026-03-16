/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.legacy.config.AgentConfig;
import com.openjiuwen.core.singleagent.legacy.schema.PluginSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Legacy single-agent base class.
 */
public abstract class BaseAgent {

    protected final AgentConfig agentConfig;
    protected final List<Tool> tools = new ArrayList<>();
    protected final List<Workflow> workflows = new ArrayList<>();
    private final ContextEngine contextEngine;

    protected BaseAgent(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
        this.contextEngine = createContextEngine();
    }

    public AgentConfig getAgentConfig() {
        return agentConfig;
    }

    /**
     * Get the context engine.
     *
     * @return the context engine
     */
    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    /**
     * Create default ContextEngine from config.
     */
    private ContextEngine createContextEngine() {
        int maxRounds = 10;
        if (agentConfig != null) {
            try {
                var constrainField = agentConfig.getClass().getMethod("getConstrain");
                var constrain = constrainField.invoke(agentConfig);
                if (constrain != null) {
                    var roundsMethod = constrain.getClass().getMethod("getReservedMaxChatRounds");
                    maxRounds = (int) roundsMethod.invoke(constrain);
                }
            } catch (Exception ignored) {
                // Config may not have constrain field
            }
        }
        return new ContextEngine(ContextEngineConfig.builder()
                .maxContextMessageNum(maxRounds * 2)
                .build());
    }

    /**
     * Add prompt template entries to configuration.
     *
     * @param promptTemplate list of prompt dicts to append
     */
    public void addPrompt(List<Map<String, String>> promptTemplate) {
        if (promptTemplate == null || promptTemplate.isEmpty()) {
            return;
        }
        try {
            var method = agentConfig.getClass().getMethod("getPromptTemplate");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> existing = (List<Map<String, String>>) method.invoke(agentConfig);
            if (existing != null) {
                existing.addAll(promptTemplate);
            } else {
                Loggers.AGENT.warning(agentConfig.getClass().getSimpleName()
                        + " has no promptTemplate field, addPrompt operation ignored");
            }
        } catch (Exception e) {
            Loggers.AGENT.warning(agentConfig.getClass().getSimpleName()
                    + " has no promptTemplate field, addPrompt operation ignored");
        }
    }

    public void addTools(List<Tool> newTools) {
        if (newTools == null || newTools.isEmpty()) {
            return;
        }
        for (Tool tool : newTools) {
            tools.add(tool);
            Runner.resourceMgr().addTool(tool, agentConfig.getId());
            if (!agentConfig.getTools().contains(tool.getCard().getName())) {
                agentConfig.getTools().add(tool.getCard().getName());
            }
        }
    }

    public void addWorkflows(List<Workflow> newWorkflows) {
        if (newWorkflows == null || newWorkflows.isEmpty()) {
            return;
        }
        for (Workflow workflow : newWorkflows) {
            workflows.add(workflow);
            Runner.resourceMgr().addWorkflow(workflow.getCard(), () -> workflow, agentConfig.getId());
        }
    }

    /**
     * Remove workflows from agent (update config and resource manager).
     *
     * @param workflowKeys list of [workflowId, workflowVersion] pairs to remove
     */
    public void removeWorkflows(List<String[]> workflowKeys) {
        if (workflowKeys == null || workflowKeys.isEmpty()) {
            return;
        }
        for (String[] keyPair : workflowKeys) {
            if (keyPair.length < 2) {
                continue;
            }
            String workflowId = keyPair[0];
            String workflowVersion = keyPair[1];
            String workflowKey = WorkflowUtils.generateWorkflowKey(workflowId, workflowVersion);

            // Remove from config
            agentConfig.getWorkflows().removeIf(
                    w -> workflowId.equals(w.getId()) && workflowVersion.equals(w.getVersion()));

            // Remove from resource manager
            try {
                Runner.resourceMgr().removeWorkflow(workflowKey, null, TagMatchStrategy.ALL, true);
            } catch (Exception e) {
                Loggers.AGENT.warning("Failed to remove workflow from resource manager: " + e.getMessage());
            }
        }
    }

    /**
     * Bind workflows - backward compatible alias for addWorkflows.
     *
     * @param newWorkflows workflows to bind
     */
    public void bindWorkflows(List<Workflow> newWorkflows) {
        addWorkflows(newWorkflows);
    }

    /**
     * Add plugin schemas to configuration.
     *
     * @param plugins list of PluginSchema to add
     */
    public void addPlugins(List<PluginSchema> plugins) {
        if (plugins == null || plugins.isEmpty()) {
            return;
        }
        try {
            var method = agentConfig.getClass().getMethod("getPlugins");
            @SuppressWarnings("unchecked")
            List<PluginSchema> existingPlugins = (List<PluginSchema>) method.invoke(agentConfig);
            if (existingPlugins != null) {
                Set<String> existingNames = existingPlugins.stream()
                        .map(PluginSchema::getName)
                        .collect(Collectors.toSet());
                for (PluginSchema plugin : plugins) {
                    if (!existingNames.contains(plugin.getName())) {
                        existingPlugins.add(plugin);
                        existingNames.add(plugin.getName());
                    }
                }
            } else {
                Loggers.AGENT.warning(agentConfig.getClass().getSimpleName()
                        + " has no plugins field, addPlugins operation ignored");
            }
        } catch (Exception e) {
            Loggers.AGENT.warning(agentConfig.getClass().getSimpleName()
                    + " has no plugins field, addPlugins operation ignored");
        }
    }

    public void clearSession(String sessionId) {
        Runner.release(sessionId);
    }

    public abstract Object invoke(Map<String, Object> inputs, Session session);

    public abstract Iterator<Object> stream(Map<String, Object> inputs, Session session);
}
