/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow;

/**
 * Configuration for a workflow instance.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.workflow_config.WorkflowConfig}.
 */
public class WorkflowConfig {

    private WorkflowCard card;
    private WorkflowSpec spec;
    private int workflowMaxNestingDepth = 5;

    public WorkflowConfig() {
        this.spec = new WorkflowSpec();
    }

    public WorkflowConfig(WorkflowCard card) {
        this.card = card;
        this.spec = new WorkflowSpec();
    }

    public WorkflowCard getCard() {
        return card;
    }

    public void setCard(WorkflowCard card) {
        this.card = card;
    }

    public WorkflowSpec getSpec() {
        return spec;
    }

    public void setSpec(WorkflowSpec spec) {
        this.spec = spec;
    }

    public int getWorkflowMaxNestingDepth() {
        return workflowMaxNestingDepth;
    }

    public void setWorkflowMaxNestingDepth(int workflowMaxNestingDepth) {
        if (workflowMaxNestingDepth < 0) {
            workflowMaxNestingDepth = 0;
        }
        if (workflowMaxNestingDepth > 10) {
            workflowMaxNestingDepth = 10;
        }
        this.workflowMaxNestingDepth = workflowMaxNestingDepth;
    }
}
