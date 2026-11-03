/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.session.tracer.TracerDecorator;
import com.openjiuwen.core.workflow.Workflow;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Workflow resource manager.
 *
 * <p>Mirrors Python's {@code WorkflowMgr} in
 * {@code openjiuwen/core/runner/resources_manager/workflow_manager.py}.</p>
 */
public class WorkflowManager extends AbstractManager<Workflow> {

    public void addWorkflow(String workflowId, Supplier<?> workflow) {
        registerResourceProvider(workflowId, workflow);
    }

    public void addWorkflows(List<WorkflowEntry> workflows) {
        if (workflows == null || workflows.isEmpty()) {
            return;
        }
        for (WorkflowEntry entry : workflows) {
            registerResourceProvider(entry.workflowId(), entry.workflow());
        }
    }

    public CompletionStage<Workflow> getWorkflow(String workflowId) {
        return getWorkflow(workflowId, null);
    }

    public CompletionStage<Workflow> getWorkflow(String workflowId, Object session) {
        return getResource(workflowId)
                .thenApply(workflow -> TracerDecorator.decorateWorkflowWithTrace(workflow, session));
    }

    public Supplier<?> removeWorkflow(String workflowId) {
        return unregisterResourceProvider(workflowId);
    }

    /**
     * Typed Java carrier for Python's {@code tuple[str, WorkflowProvider]} items
     * in {@code WorkflowMgr.add_workflows}.
     *
     * <p>Mirrors Python's {@code WorkflowMgr.add_workflows} in
     * {@code openjiuwen/core/runner/resources_manager/workflow_manager.py}.</p>
     *
     * @param workflowId workflow id
     * @param workflow workflow provider
     */
    public record WorkflowEntry(String workflowId, Supplier<?> workflow) {
    }
}
