/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.legacy;

import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.function.Supplier;

/**
 * Workflow factory class that creates a new workflow instance on each call.
 *
 * <p>Mirrors Python's {@code WorkflowFactory} in
 * {@code openjiuwen.core.single_agent.legacy.agent}.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Callable: provider() returns a new workflow instance each time</li>
 *   <li>Provides id/version attributes for workflow key generation</li>
 *   <li>Auto-sets workflow metadata on each call</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowFactory {

    private WorkflowCard workflowCard;
    private Supplier<Workflow> factory;

    /**
     * Create WorkflowFactory.
     *
     * @param workflowId        Workflow ID for registration
     * @param workflowVersion   Workflow version for registration
     * @param factory           Factory function that returns a new Workflow instance
     */
    public WorkflowFactory(
            String workflowId,
            String workflowVersion,
            Supplier<Workflow> factory,
            String workflowName,
            String workflowDescription,
            Object inputSchema
    ) {
        this.workflowCard = WorkflowCard.builder()
                .id(workflowId)
                .name(workflowName)
                .description(workflowDescription)
                .version(workflowVersion)
                .build();
        this.factory = factory;
    }

    /**
     * Get the workflow card.
     *
     * @return the workflow card
     */
    public WorkflowCard card() {
        return workflowCard;
    }

    /**
     * Return a new workflow instance on each call.
     *
     * @return new Workflow instance
     */
    public Workflow call() {
        return factory.get();
    }
}