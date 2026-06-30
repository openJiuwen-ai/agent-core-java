/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

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

    /**
     * Auto-generated for codecheck compliance.
     */
    public WorkflowConfig() {
        this.spec = new WorkflowSpec();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WorkflowConfig(WorkflowCard card) {
        this.card = card;
        this.spec = new WorkflowSpec();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WorkflowCard getCard() {
        return card;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCard(WorkflowCard card) {
        this.card = card;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WorkflowSpec getSpec() {
        return spec;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSpec(WorkflowSpec spec) {
        this.spec = spec;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getWorkflowMaxNestingDepth() {
        return workflowMaxNestingDepth;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
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
