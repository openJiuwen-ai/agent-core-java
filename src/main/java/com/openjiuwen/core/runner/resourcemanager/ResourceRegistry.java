/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

/**
 * Central registry holding all sub-managers for different resource types.
 * <p>
 * Mirrors Python's {@code ResourceRegistry} in {@code resources_manager/resource_registry.py}.
 * 
 * @since 0.1.7
 */
public class ResourceRegistry {
    private final ToolMgr toolMgr = new ToolMgr();

    /**
     * WorkflowMgr.
     * 
     * @since 0.1.7
     */
    private final WorkflowMgr workflowMgr = new WorkflowMgr();

    /**
     * PromptMgr.
     * 
     * @since 0.1.7
     */
    private final PromptMgr promptMgr = new PromptMgr();

    /**
     * ModelMgr.
     * 
     * @since 0.1.7
     */
    private final ModelMgr modelMgr = new ModelMgr();

    /**
     * AgentMgr<>.
     * 
     * @since 0.1.7
     */
    private final AgentMgr<Object> agentMgr = new AgentMgr<>();

    /**
     * AgentGroupMgr<>.
     * 
     * @since 0.1.7
     */
    private final AgentGroupMgr<Object> agentGroupMgr = new AgentGroupMgr<>();

    /**
     * SysOperationMgr.
     * 
     * @since 0.1.7
     */
    private final SysOperationMgr sysOperationMgr = new SysOperationMgr();

    /**
     * Clear all registered resources across all sub-managers.
     * 
     * @since 0.1.7
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

    /**
     * removeById.
     * 
     * @param resourceId resourceId
     * @since 0.1.7
     */
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

    /**
     * tool.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ToolMgr tool() {
        return toolMgr;
    }

    /**
     * prompt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public PromptMgr prompt() {
        return promptMgr;
    }

    /**
     * model.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ModelMgr model() {
        return modelMgr;
    }

    /**
     * workflow.
     * 
     * @return the result
     * @since 0.1.7
     */
    public WorkflowMgr workflow() {
        return workflowMgr;
    }

    /**
     * agent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public AgentMgr<Object> agent() {
        return agentMgr;
    }

    /**
     * agentGroup.
     * 
     * @return the result
     * @since 0.1.7
     */
    public AgentGroupMgr<Object> agentGroup() {
        return agentGroupMgr;
    }

    /**
     * sysOperation.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SysOperationMgr sysOperation() {
        return sysOperationMgr;
    }
}
