/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.DLTransformer;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer.WorkflowDesigner;

import java.util.List;

/**
 * Package-level compatibility exports for the workflow builder package.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.builders.workflow} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/__init__.py}.</p>
 */
public final class WorkflowBuilderPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/dev_tools/agent_builder/builders/workflow/__init__.py";
    public static final List<String> ALL = List.of(
            "WorkflowBuilder",
            "IntentionDetector",
            "WorkflowDesigner",
            "DLGenerator",
            "Reflector",
            "DLTransformer",
            "CycleChecker"
    );
    public static final Class<WorkflowBuilder> WORKFLOW_BUILDER = WorkflowBuilder.class;
    public static final Class<IntentionDetector> INTENTION_DETECTOR = IntentionDetector.class;
    public static final Class<WorkflowDesigner> WORKFLOW_DESIGNER = WorkflowDesigner.class;
    public static final Class<DLGenerator> DL_GENERATOR = DLGenerator.class;
    public static final Class<Reflector> REFLECTOR = Reflector.class;
    public static final Class<DLTransformer> DL_TRANSFORMER = DLTransformer.class;
    public static final Class<CycleChecker> CYCLE_CHECKER = CycleChecker.class;

    private WorkflowBuilderPackage() {
    }
}
