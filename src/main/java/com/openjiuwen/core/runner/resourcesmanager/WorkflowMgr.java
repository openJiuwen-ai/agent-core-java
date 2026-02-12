// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.tracer.TracerDecorator;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Workflow管理器
 * 
 * 对应Python: resources_manager/workflow_manager.py - WorkflowMgr
 * 
 * @param <T> Workflow类型
 */
public class WorkflowMgr<T> extends AbstractManager<T> {
    
    public WorkflowMgr() {
        super();
    }
    
    /**
     * 添加Workflow（同步Provider）
     * 
     * @param workflowId Workflow ID
     * @param provider 同步Provider
     */
    public void addWorkflow(String workflowId, Supplier<T> provider) {
        registerResourceProvider(workflowId, provider);
    }
    
    /**
     * 添加Workflow（异步Provider）
     * 
     * @param workflowId Workflow ID
     * @param provider 异步Provider
     */
    public void addAsyncWorkflow(String workflowId, Supplier<CompletableFuture<T>> provider) {
        registerAsyncResourceProvider(workflowId, provider);
    }
    
    /**
     * 批量添加Workflows
     * 
     * @param workflows Workflow条目列表
     */
    @SuppressWarnings("unchecked")
    public void addWorkflows(List<WorkflowEntry> workflows) {
        if (workflows == null || workflows.isEmpty()) {
            return;
        }
        for (WorkflowEntry entry : workflows) {
            registerResourceProvider(entry.workflowId(), (Supplier<T>) entry.provider());
        }
    }
    
    /**
     * 移除Workflow
     * 
     * @param workflowId Workflow ID
     * @return 被移除的Provider，如果不存在返回null
     */
    public Supplier<?> removeWorkflow(String workflowId) {
        return unregisterResourceProvider(workflowId);
    }
    
    /**
     * 获取Workflow（带Trace装饰）
     * 
     * @param workflowId Workflow ID
     * @param session 会话（用于Trace装饰，可为null）
     * @return 包含TracedWorkflow的CompletableFuture
     */
    public CompletableFuture<TracerDecorator.TracedWorkflow<T>> getWorkflow(String workflowId, AgentSession session) {
        return getResourceAsync(workflowId).thenApply(workflow -> {
            if (workflow == null) {
                return null;
            }
            // 使用适配器将 AgentSession 适配到 TracerDecorator.AgentSession
            TracerDecorator.AgentSession adaptedSession = AgentSessionAdapter.of(session);
            return TracerDecorator.decorateWorkflowWithTrace(workflow, adaptedSession);
        });
    }
    
    /**
     * 获取原始Workflow（不带Trace装饰）
     * 
     * @param workflowId Workflow ID
     * @return 包含原始Workflow的CompletableFuture
     */
    public CompletableFuture<T> getRawWorkflow(String workflowId) {
        return getResourceAsync(workflowId);
    }
    
    /**
     * Workflow条目记录
     */
    public record WorkflowEntry(String workflowId, Supplier<?> provider) {}
}

