/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.legacy.config.AgentConfig;
import com.openjiuwen.core.workflow.Workflow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Legacy single-agent base class.
 */
public abstract class BaseAgent {

    protected final AgentConfig agentConfig;
    protected final List<Tool> tools = new ArrayList<>();
    protected final List<Workflow> workflows = new ArrayList<>();

    protected BaseAgent(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
    }

    public AgentConfig getAgentConfig() {
        return agentConfig;
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

    public void clearSession(String sessionId) {
        Runner.release(sessionId);
    }

    public abstract Object invoke(Map<String, Object> inputs, Session session);

    public abstract Iterator<Object> stream(Map<String, Object> inputs, Session session);
}
