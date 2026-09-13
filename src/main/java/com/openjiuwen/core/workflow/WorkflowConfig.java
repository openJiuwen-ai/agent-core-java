/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

/**
 * Configuration for a workflow instance.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.workflow_config.WorkflowConfig}.
 * 
 * @since 0.1.7
 */
public class WorkflowConfig {
    private WorkflowCard card;
    private WorkflowSpec spec;
    private int workflowMaxNestingDepth = 5;

    /**
     * WorkflowConfig.
     * 
     * @since 0.1.7
     */
    public WorkflowConfig() {
        this.spec = new WorkflowSpec();
    }

    /**
     * WorkflowConfig.
     * 
     * @param card card
     * @since 0.1.7
     */
    public WorkflowConfig(WorkflowCard card) {
        this.card = card;
        this.spec = new WorkflowSpec();
    }

    /**
     * getCard.
     * 
     * @return the result
     * @since 0.1.7
     */
    public WorkflowCard getCard() {
        return card;
    }

    /**
     * setCard.
     * 
     * @param card card
     * @since 0.1.7
     */
    public void setCard(WorkflowCard card) {
        this.card = card;
    }

    /**
     * getSpec.
     * 
     * @return the result
     * @since 0.1.7
     */
    public WorkflowSpec getSpec() {
        return spec;
    }

    /**
     * setSpec.
     * 
     * @param spec spec
     * @since 0.1.7
     */
    public void setSpec(WorkflowSpec spec) {
        this.spec = spec;
    }

    /**
     * getWorkflowMaxNestingDepth.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getWorkflowMaxNestingDepth() {
        return workflowMaxNestingDepth;
    }

    /**
     * setWorkflowMaxNestingDepth.
     * 
     * @param workflowMaxNestingDepth workflowMaxNestingDepth
     * @since 0.1.7
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
