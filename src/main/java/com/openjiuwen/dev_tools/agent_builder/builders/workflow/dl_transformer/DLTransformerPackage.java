/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for DL transformer public exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer}
 * in {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/__init__.py}.</p>
 */
public final class DLTransformerPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/__init__.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "DLTransformer",
            "Workflow",
            "Node",
            "Edge",
            "NodeType",
            "Position"
    );
    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private DLTransformerPackage() {
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("DLTransformer", DLTransformer.class);
        exports.put("Workflow", Workflow.class);
        exports.put("Node", Node.class);
        exports.put("Edge", Edge.class);
        exports.put("NodeType", NodeType.class);
        exports.put("Position", Position.class);
        return Collections.unmodifiableMap(exports);
    }
}
