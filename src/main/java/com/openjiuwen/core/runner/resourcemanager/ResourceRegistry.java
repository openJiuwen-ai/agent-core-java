/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

/**
 * Central registry holding all sub-managers for different resource types.
 *
 * <p>Mirrors Python's {@code ResourceRegistry} in
 * {@code openjiuwen/core/runner/resources_manager/resource_registry.py}.</p>
 */
public class ResourceRegistry {

    private ToolMgr toolMgr = new ToolMgr();
    private WorkflowMgr workflowMgr = new WorkflowMgr();
    private PromptMgr promptMgr = new PromptMgr();
    private ModelMgr modelMgr = new ModelMgr();
    private AgentMgr<Object> agentMgr = new AgentMgr<>();
    private AgentGroupMgr<Object> agentGroupMgr = new AgentGroupMgr<>();
    private SysOperationMgr sysOperationMgr = new SysOperationMgr();

    /**
     * Clears all registered resources across all sub-managers.
     */
    public void clearAll() {
        toolMgr = new ToolMgr();
        workflowMgr = new WorkflowMgr();
        promptMgr = new PromptMgr();
        modelMgr = new ModelMgr();
        agentMgr = new AgentMgr<>();
        agentGroupMgr = new AgentGroupMgr<>();
        sysOperationMgr = new SysOperationMgr();
    }

    public void removeById(String resourceId) {
        if (toolMgr.removeTool(resourceId) != null) {
            return;
        }
        if (workflowMgr.removeWorkflow(resourceId) != null) {
            return;
        }
        if (agentMgr.removeAgent(resourceId) != null) {
            return;
        }
        if (agentGroupMgr.removeAgentGroup(resourceId) != null) {
            return;
        }
        if (promptMgr.removePrompt(resourceId) != null) {
            return;
        }
        if (modelMgr.removeModel(resourceId) != null) {
            return;
        }
        sysOperationMgr.removeSysOperation(resourceId);
    }

    public ToolMgr tool() {
        return toolMgr;
    }

    public PromptMgr prompt() {
        return promptMgr;
    }

    public ModelMgr model() {
        return modelMgr;
    }

    public WorkflowMgr workflow() {
        return workflowMgr;
    }

    public AgentMgr<Object> agent() {
        return agentMgr;
    }

    public AgentGroupMgr<Object> agentGroup() {
        return agentGroupMgr;
    }

    public SysOperationMgr sysOperation() {
        return sysOperationMgr;
    }

    public AgentGroupMgr<Object> agentTeam() {
        return agentGroupMgr;
    }

    public ToolManager toolManager() {
        return toolMgr.asToolManager();
    }

    public WorkflowManager workflowManager() {
        return workflowMgr;
    }

    public PromptManager promptManager() {
        return promptMgr;
    }

    public ModelManager modelManager() {
        return modelMgr;
    }

    public AgentManager agentManager() {
        return agentMgr;
    }

    public AgentTeamManager agentTeamManager() {
        return agentGroupMgr;
    }

    public SysOperationManager sysOperationManager() {
        return sysOperationMgr;
    }
}
