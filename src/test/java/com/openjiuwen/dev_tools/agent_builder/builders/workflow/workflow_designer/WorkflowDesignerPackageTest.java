/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's package export behavior in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/workflow_designer/__init__.py}.
 */
class WorkflowDesignerPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertThat(WorkflowDesignerPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/dev_tools/agent_builder/builders/workflow/workflow_designer/__init__.py");
        assertThat(WorkflowDesignerPackage.EXPORTED_SYMBOLS)
                .containsExactlyElementsOf(List.of("WorkflowDesigner"));
        assertThat(WorkflowDesignerPackage.EXPORTED_TYPES)
                .containsEntry("WorkflowDesigner", WorkflowDesigner.class);
    }
}
