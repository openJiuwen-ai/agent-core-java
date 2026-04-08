/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.workflow.Workflow;

import java.util.List;
import java.util.function.Supplier;

/**
 * Manager for Workflow resource providers.
 * Mirrors Python's {@code WorkflowMgr} in {@code resources_manager/workflow_manager.py}.
 */
public class WorkflowMgr extends AbstractManager<Workflow> {

    public void addWorkflow(String workflowId, Supplier<Workflow> workflow) {
        registerResourceProvider(workflowId, workflow);
    }

    public void addWorkflows(List<WorkflowEntry> workflows) {
        if (workflows == null || workflows.isEmpty()) {
            return;
        }
        for (WorkflowEntry entry : workflows) {
            registerResourceProvider(entry.id(), entry.provider());
        }
    }

    public Workflow getWorkflow(String workflowId) {
        return getResource(workflowId);
    }

    public Supplier<? extends Workflow> removeWorkflow(String workflowId) {
        return unregisterResourceProvider(workflowId);
    }

    public record WorkflowEntry(String id, Supplier<Workflow> provider) {
    }
}
