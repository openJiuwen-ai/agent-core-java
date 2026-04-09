/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

/**
 * Central registry holding all sub-managers for different resource types.
 * <p>
 * Mirrors Python's {@code ResourceRegistry} in {@code resources_manager/resource_registry.py}.
 */
public class ResourceRegistry {

    private final ToolMgr toolMgr = new ToolMgr();
    private final WorkflowMgr workflowMgr = new WorkflowMgr();
    private final PromptMgr promptMgr = new PromptMgr();
    private final ModelMgr modelMgr = new ModelMgr();
    private final AgentMgr<Object> agentMgr = new AgentMgr<>();
    private final AgentGroupMgr<Object> agentGroupMgr = new AgentGroupMgr<>();
    private final SysOperationMgr sysOperationMgr = new SysOperationMgr();

    /**
     * Clear all registered resources across all sub-managers.
     */
    public void clearAll() {
        toolMgr.release();
        workflowMgr.clearProviders();
        promptMgr.clear();
        modelMgr.clearProviders();
        agentMgr.clearProviders();
        agentGroupMgr.clearProviders();
        sysOperationMgr.clear();
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
}
