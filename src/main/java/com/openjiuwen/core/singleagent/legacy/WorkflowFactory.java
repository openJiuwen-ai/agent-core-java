/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Root-package compatibility alias for legacy workflow providers.
 *
 * <p>Mirrors Python's {@code WorkflowFactory} in
 * {@code openjiuwen/core/single_agent/legacy/agent.py}.</p>
 */
@Deprecated(since = "0.1.7", forRemoval = true)
public class WorkflowFactory implements Supplier<Workflow> {
    private final Supplier<Workflow> factory;
    private final WorkflowCard workflowCard;

    public WorkflowFactory(String workflowId, String workflowVersion, Supplier<Workflow> factory) {
        this(workflowId, workflowVersion, factory, "", "", null);
    }

    public WorkflowFactory(String workflowId, String workflowVersion, Supplier<Workflow> factory,
                           String workflowName, String workflowDescription, Object inputSchema) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.workflowCard = new WorkflowCard(
                workflowId,
                workflowName == null ? "" : workflowName,
                workflowDescription == null ? "" : workflowDescription,
                workflowVersion,
                inputSchema);
    }

    public WorkflowCard card() {
        return workflowCard;
    }

    @Override
    public Workflow get() {
        return factory.get();
    }

    public static WorkflowFactory workflowProvider(String workflowId, String workflowVersion,
                                                   Supplier<Workflow> factory) {
        return workflowProvider(workflowId, workflowVersion, "", "", null, factory);
    }

    public static WorkflowFactory workflowProvider(String workflowId, String workflowVersion,
                                                   String workflowName, String workflowDescription,
                                                   Object inputSchema, Supplier<Workflow> factory) {
        return new WorkflowFactory(
                workflowId,
                workflowVersion,
                factory,
                workflowName,
                workflowDescription,
                inputSchema);
    }

    public static WorkflowFactory workflow_provider(String workflowId, String workflowVersion,
                                                    String workflowName, String workflowDescription,
                                                    Object inputSchema, Supplier<Workflow> factory) {
        return workflowProvider(workflowId, workflowVersion, workflowName, workflowDescription, inputSchema, factory);
    }
}
