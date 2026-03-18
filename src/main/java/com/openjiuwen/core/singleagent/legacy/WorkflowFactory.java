/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;

import java.util.function.Supplier;

/**
 * Workflow factory that creates a new workflow instance on each call (concurrency-safe).
 *
 * <p>Mirrors Python's {@code WorkflowFactory} in {@code single_agent/legacy/agent.py}.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * WorkflowFactory provider = new WorkflowFactory("my_workflow", "1.0", () -> buildWorkflow());
 * agent.addWorkflows(List.of(provider));
 * }</pre>
 * </p>
 */
public class WorkflowFactory implements Supplier<Workflow> {

    private final Supplier<Workflow> factory;
    private final WorkflowCard workflowCard;

    /**
     * Create a WorkflowFactory.
     *
     * @param workflowId          workflow ID for registration
     * @param workflowVersion     workflow version for registration
     * @param factory             factory function returning a new Workflow each call
     * @param workflowName        workflow name (optional)
     * @param workflowDescription workflow description (optional)
     * @param inputSchema         input schema (optional)
     */
    public WorkflowFactory(String workflowId, String workflowVersion,
                           Supplier<Workflow> factory,
                           String workflowName, String workflowDescription,
                           Object inputSchema) {
        this.factory = factory;
        this.workflowCard = WorkflowCard.builder()
                .id(workflowId)
                .name(workflowName != null ? workflowName : "")
                .description(workflowDescription != null ? workflowDescription : "")
                .version(workflowVersion)
                .inputParams(inputSchema)
                .build();
    }

    public WorkflowFactory(String workflowId, String workflowVersion, Supplier<Workflow> factory) {
        this(workflowId, workflowVersion, factory, "", "", null);
    }

    public WorkflowCard card() {
        return workflowCard;
    }

    /**
     * Return a new workflow instance on each call.
     *
     * @return a new Workflow instance
     */
    @Override
    public Workflow get() {
        return factory.get();
    }
}
