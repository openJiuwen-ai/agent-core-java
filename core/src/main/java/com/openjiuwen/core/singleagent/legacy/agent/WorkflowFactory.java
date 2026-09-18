/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.agent;

import com.openjiuwen.core.workflow.WorkflowCard;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Workflow provider wrapper used by the legacy single-agent API.
 *
 * <p>Mirrors Python's {@code WorkflowFactory} in
 * {@code openjiuwen/core/single_agent/legacy/agent.py}.</p>
 */
public final class WorkflowFactory implements Supplier<Object> {
    private final Supplier<?> factory;
    private final WorkflowCard workflowCard;

    public WorkflowFactory(String workflowId, String workflowVersion, Supplier<?> factory) {
        this(workflowId, workflowVersion, factory, "", "", null);
    }

    public WorkflowFactory(String workflowId, String workflowVersion, Supplier<?> factory,
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
    public Object get() {
        return factory.get();
    }

    public static WorkflowFactory workflowProvider(String workflowId, String workflowVersion, Supplier<?> factory) {
        return workflowProvider(workflowId, workflowVersion, "", "", null, factory);
    }

    public static WorkflowFactory workflowProvider(String workflowId, String workflowVersion,
                                                   String workflowName, String workflowDescription,
                                                   Object inputs, Supplier<?> factory) {
        return new WorkflowFactory(workflowId, workflowVersion, factory, workflowName, workflowDescription, inputs);
    }

    public static WorkflowFactory workflow_provider(String workflowId, String workflowVersion,
                                                    String workflowName, String workflowDescription,
                                                    Object inputs, Supplier<?> factory) {
        return workflowProvider(workflowId, workflowVersion, workflowName, workflowDescription, inputs, factory);
    }
}
