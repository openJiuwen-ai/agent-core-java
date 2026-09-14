/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Workflow runtime configuration.
 * <p>
 * Mirrors Python's {@code WorkflowConfig} in
 * {@code openjiuwen/core/workflow/workflow_config.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowConfig {

    private WorkflowCard card;

    private WorkflowSpec spec = new WorkflowSpec();

    @JsonProperty("workflow_max_nesting_depth")
    private int workflowMaxNestingDepth = 5;

    public WorkflowConfig() {
    }

    public WorkflowConfig(WorkflowCard card) {
        setCard(card);
    }

    public WorkflowCard getCard() {
        return card;
    }

    public void setCard(WorkflowCard card) {
        this.card = Objects.requireNonNull(card, "card must not be null");
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
        if (workflowMaxNestingDepth < 0 || workflowMaxNestingDepth > 10) {
            throw new IllegalArgumentException("workflow_max_nesting_depth must be between 0 and 10");
        }
        this.workflowMaxNestingDepth = workflowMaxNestingDepth;
    }
}
