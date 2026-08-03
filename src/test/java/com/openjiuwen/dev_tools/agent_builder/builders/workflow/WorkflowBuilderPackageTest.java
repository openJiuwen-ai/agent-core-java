/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.DLTransformer;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer.WorkflowDesigner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.builders.workflow} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/__init__.py}.
 */
class WorkflowBuilderPackageTest {

    @Test
    void exportsMirrorPythonAllOrderAndClasses() {
        assertEquals(
                "openjiuwen/dev_tools/agent_builder/builders/workflow/__init__.py",
                WorkflowBuilderPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "WorkflowBuilder",
                "IntentionDetector",
                "WorkflowDesigner",
                "DLGenerator",
                "Reflector",
                "DLTransformer",
                "CycleChecker"
        ), WorkflowBuilderPackage.ALL);
        assertSame(WorkflowBuilder.class, WorkflowBuilderPackage.WORKFLOW_BUILDER);
        assertSame(IntentionDetector.class, WorkflowBuilderPackage.INTENTION_DETECTOR);
        assertSame(WorkflowDesigner.class, WorkflowBuilderPackage.WORKFLOW_DESIGNER);
        assertSame(DLGenerator.class, WorkflowBuilderPackage.DL_GENERATOR);
        assertSame(Reflector.class, WorkflowBuilderPackage.REFLECTOR);
        assertSame(DLTransformer.class, WorkflowBuilderPackage.DL_TRANSFORMER);
        assertSame(CycleChecker.class, WorkflowBuilderPackage.CYCLE_CHECKER);
    }
}
