/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package facade for workflow designer exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer}
 * in {@code openjiuwen/dev_tools/agent_builder/builders/workflow/workflow_designer/__init__.py}.</p>
 */
public final class WorkflowDesignerPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/dev_tools/agent_builder/builders/workflow/workflow_designer/__init__.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of("WorkflowDesigner");
    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private WorkflowDesignerPackage() {
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("WorkflowDesigner", WorkflowDesigner.class);
        return Collections.unmodifiableMap(exports);
    }
}
