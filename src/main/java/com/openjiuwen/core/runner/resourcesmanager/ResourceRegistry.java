// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

/**
 * 资源注册表，聚合所有具体管理器
 * 
 * 对应Python: resources_manager/resource_registry.py - ResourceRegistry
 */
public class ResourceRegistry {

    private final ToolMgr toolMgr;
    private final WorkflowMgr<Object> workflowMgr;
    private final PromptMgr promptMgr;
    private final ModelMgr<Object> modelMgr;
    private final AgentMgr<Object> agentMgr;
    private final AgentGroupMgr<Object> agentGroupMgr;
    private final SysOperationMgr sysOperationMgr;

    public ResourceRegistry() {
        this.toolMgr = new ToolMgr();
        this.workflowMgr = new WorkflowMgr<>();
        this.promptMgr = new PromptMgr();
        this.modelMgr = new ModelMgr<>();
        this.agentMgr = new AgentMgr<>();
        this.agentGroupMgr = new AgentGroupMgr<>();
        this.sysOperationMgr = new SysOperationMgr();
    }

    /**
     * 按优先级遍历各管理器，删除第一个匹配的资源
     * 
     * @param resourceId 资源ID
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
     * 获取Tool管理器
     */
    public ToolMgr tool() {
        return toolMgr;
    }

    /**
     * 获取Prompt管理器
     */
    public PromptMgr prompt() {
        return promptMgr;
    }

    /**
     * 获取Model管理器
     */
    @SuppressWarnings("unchecked")
    public <T> ModelMgr<T> model() {
        return (ModelMgr<T>) modelMgr;
    }

    /**
     * 获取Workflow管理器
     */
    @SuppressWarnings("unchecked")
    public <T> WorkflowMgr<T> workflow() {
        return (WorkflowMgr<T>) workflowMgr;
    }

    /**
     * 获取Agent管理器
     */
    @SuppressWarnings("unchecked")
    public <T> AgentMgr<T> agent() {
        return (AgentMgr<T>) agentMgr;
    }

    /**
     * 获取AgentGroup管理器
     */
    @SuppressWarnings("unchecked")
    public <T> AgentGroupMgr<T> agentGroup() {
        return (AgentGroupMgr<T>) agentGroupMgr;
    }

    /**
     * 获取SysOperation管理器
     */
    public SysOperationMgr sysOperation() {
        return sysOperationMgr;
    }
}

