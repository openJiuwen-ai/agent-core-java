/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for WorkflowDesigner integration.
 * <p>
 * Mirrors Python's {@code test_workflow_designer_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.workflow_designer}.
 */
class TestWorkflowDesignerIntegration {

    @Test
    void designerCreation() {
        WorkflowDesigner designer = new WorkflowDesigner(null);
        assertThat(designer).isNotNull();
    }

    @Test
    void designerWithNonNullLlm() {
        Object mockLlm = new Object();
        WorkflowDesigner designer = new WorkflowDesigner(mockLlm);
        assertThat(designer).isNotNull();
    }
}
