/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

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
import java.util.function.Supplier;
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

    // ======================== Config wrapper ========================

    /**
     * Lightweight wrapper that mirrors Python's {@code Config} object returned
     * by {@code legacy.BaseAgent.config()}.
     */
    public static class Config {
        private final AgentConfig agentConfig;

        Config(AgentConfig agentConfig) {
            this.agentConfig = agentConfig;
        }

        /**
         * Backward-compatible accessor.
         *
         * @return the wrapped {@link AgentConfig}
         */
        public AgentConfig getAgentConfig() {
            return agentConfig;
        }
    }

    /**
     * Return a {@link Config} wrapper – mirrors Python's
     * {@code agent.config().get_agent_config()} call chain.
     *
     * @return a Config wrapper around this agent's configuration
     */
    public Config config() {
        return new Config(agentConfig);
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
     * Add workflows from a heterogeneous list that may contain {@link Workflow}
     * instances, {@link WorkflowFactory} objects, or generic {@link Supplier}
     * providers with a {@code card()} method.
     *
     * <p>Mirrors Python's {@code BaseAgent.add_workflows(List[Union[Workflow,
     * Callable[[], Workflow]]])}.</p>
     *
     * @param items list of Workflow / WorkflowFactory / Supplier&lt;Workflow&gt;
     */
    @SuppressWarnings("unchecked")
    public void addWorkflowItems(List<?> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (Object item : items) {
            if (item instanceof WorkflowFactory factory) {
                Workflow wf = factory.get();
                workflows.add(wf);
                Runner.resourceMgr().addWorkflow(factory.card(), factory, agentConfig.getId());
            } else if (item instanceof Workflow workflow) {
                workflows.add(workflow);
                Runner.resourceMgr().addWorkflow(workflow.getCard(), () -> workflow, agentConfig.getId());
            } else if (item instanceof Supplier<?> supplier) {
                Object result = supplier.get();
                if (result instanceof Workflow wf) {
                    workflows.add(wf);
                    Runner.resourceMgr().addWorkflow(wf.getCard(), (Supplier<Workflow>) supplier, agentConfig.getId());
                } else {
                    Loggers.AGENT.warning("Supplier returned non-Workflow object: " + result);
                }
            } else {
                Loggers.AGENT.warning("Unsupported item type in addWorkflowItems: " + item.getClass().getName());
            }
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
            agentConfig.getWorkflows().removeIf(w -> {
                if (w instanceof com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema ws) {
                    return workflowId.equals(ws.getId()) && workflowVersion.equals(ws.getVersion());
                } else if (w instanceof WorkflowCard wc) {
                    return workflowId.equals(wc.getId()) && workflowVersion.equals(wc.getVersion());
                }
                return false;
            });

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
     * Bind workflows – variant accepting heterogeneous items.
     *
     * @param items list of Workflow / WorkflowFactory / Supplier
     */
    public void bindWorkflowItems(List<?> items) {
        addWorkflowItems(items);
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
