/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

/**
 * Focused validation for {@link CodingMemoryToolContext}.
 *
 * <p>Mirrors Python's {@code CodingMemoryToolContext} in
 * {@code openjiuwen/core/memory/lite/coding_memory_tool_context.py}.</p>
 */
public final class CodingMemoryToolContextTest {

    private CodingMemoryToolContextTest() {
    }

    public static void main(String[] args) {
        defaultContextUsesCodingMemoryNode();
        constructorKeepsExplicitCodingMemoryDirectory();
        explicitNodeNameCanOverrideDataclassDefault();
        setterPreservesExplicitNullLikePythonDataclassAssignment();
        System.out.println("PASS CodingMemoryToolContextTest");
    }

    private static void defaultContextUsesCodingMemoryNode() {
        CodingMemoryToolContext context = new CodingMemoryToolContext();

        require("".equals(context.getCodingMemoryDir()), "coding memory dir default");
        require("coding_memory".equals(context.getNodeName()), "node name default");
    }

    private static void constructorKeepsExplicitCodingMemoryDirectory() {
        CodingMemoryToolContext context = new CodingMemoryToolContext("workspace/.jiuwen/coding_memory");

        require("workspace/.jiuwen/coding_memory".equals(context.getCodingMemoryDir()), "coding memory dir");
        require("coding_memory".equals(context.getNodeName()), "node name");
    }

    private static void explicitNodeNameCanOverrideDataclassDefault() {
        CodingMemoryToolContext context = new CodingMemoryToolContext("custom-dir", "custom_node");

        require("custom-dir".equals(context.getCodingMemoryDir()), "custom dir");
        require("custom_node".equals(context.getNodeName()), "custom node");
    }

    private static void setterPreservesExplicitNullLikePythonDataclassAssignment() {
        CodingMemoryToolContext context = new CodingMemoryToolContext();

        context.setCodingMemoryDir(null);

        require(context.getCodingMemoryDir() == null, "null assignment");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
