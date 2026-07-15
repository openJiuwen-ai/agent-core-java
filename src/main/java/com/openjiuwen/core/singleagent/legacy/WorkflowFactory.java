/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;

import java.util.function.Supplier;

/**
 * Workflow factory that creates a new workflow instance on each call (concurrency-safe).
 * <p>
 * Mirrors Python's {@code WorkflowFactory} in {@code single_agent/legacy/agent.py}.
 * </p>
 * <p>
 * Usage:
 * 
 * <pre>{@code
 * WorkflowFactory provider = new WorkflowFactory("my_workflow", "1.0", () -> buildWorkflow());
 * agent.addWorkflows(List.of(provider));
 * }</pre>
 * </p>
 * 
 * @since 0.1.7
 */
public class WorkflowFactory implements Supplier<Workflow> {
    private final Supplier<Workflow> factory;
    private final WorkflowCard workflowCard;

    /**
     * Create a WorkflowFactory.
     * 
     * @param workflowId workflow ID for registration
     * @param workflowVersion workflow version for registration
     * @param factory factory function returning a new Workflow each call
     * @param workflowName workflow name (optional)
     * @param workflowDescription workflow description (optional)
     * @param inputSchema input schema (optional)
     * @since 0.1.7
     */
    public WorkflowFactory(String workflowId, String workflowVersion, Supplier<Workflow> factory, String workflowName,
            String workflowDescription, Object inputSchema) {
        this.factory = factory;
        this.workflowCard = WorkflowCard.builder().id(workflowId).name(workflowName != null ? workflowName : "")
                .description(workflowDescription != null ? workflowDescription : "").version(workflowVersion)
                .inputParams(inputSchema).build();
    }

    /**
     * WorkflowFactory.
     * 
     * @param workflowId workflowId
     * @param workflowVersion workflowVersion
     * @param factory factory
     * @since 0.1.7
     */
    public WorkflowFactory(String workflowId, String workflowVersion, Supplier<Workflow> factory) {
        this(workflowId, workflowVersion, factory, "", "", null);
    }

    /**
     * card.
     * 
     * @return the result
     * @since 0.1.7
     */
    public WorkflowCard card() {
        return workflowCard;
    }

    /**
     * get.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Workflow get() {
        return factory.get();
    }
}
