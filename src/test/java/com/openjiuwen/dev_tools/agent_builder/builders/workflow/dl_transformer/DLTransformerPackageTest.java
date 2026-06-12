/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's package export behavior in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/__init__.py}.
 */
class DLTransformerPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals(
                "openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/__init__.py",
                DLTransformerPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "DLTransformer",
                "Workflow",
                "Node",
                "Edge",
                "NodeType",
                "Position"
        ), DLTransformerPackage.EXPORTED_SYMBOLS);
        assertEquals(DLTransformer.class, DLTransformerPackage.EXPORTED_TYPES.get("DLTransformer"));
        assertEquals(Workflow.class, DLTransformerPackage.EXPORTED_TYPES.get("Workflow"));
        assertEquals(Node.class, DLTransformerPackage.EXPORTED_TYPES.get("Node"));
        assertEquals(Edge.class, DLTransformerPackage.EXPORTED_TYPES.get("Edge"));
        assertEquals(NodeType.class, DLTransformerPackage.EXPORTED_TYPES.get("NodeType"));
        assertEquals(Position.class, DLTransformerPackage.EXPORTED_TYPES.get("Position"));
    }
}
