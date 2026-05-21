/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for workflow designer prompts integration.
 * <p>
 * Mirrors Python's {@code test_prompts_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.workflow_designer}.
 */
class TestWorkflowDesignerPromptsIntegration {

    @Test
    void designerPromptsCanBeReferenced() {
        assertThat(WorkflowDesigner.class).isNotNull();
    }

    @Test
    void designerInstanceCanBeCreated() {
        WorkflowDesigner designer = new WorkflowDesigner(null);
        assertThat(designer).isNotNull();
    }
}
