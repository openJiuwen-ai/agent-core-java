/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

/**
 * Focused validation for {@link MemoryToolContext}.
 *
 * <p>Mirrors Python's {@code MemoryToolContext} in
 * {@code openjiuwen/core/memory/lite/memory_tool_context.py}.</p>
 */
public final class MemoryToolContextTest {

    private MemoryToolContextTest() {
    }

    public static void main(String[] args) {
        MemoryToolContext context = new MemoryToolContext();

        require(context.getWorkspace() == null, "workspace default");
        require(context.getSettings() == null, "settings default");
        require("default".equals(context.getAgentId()), "agent id default");
        require(context.getEmbeddingConfig() == null, "embedding config default");
        require(context.getSysOperation() == null, "sys operation default");
        require(context.getManager() == null, "manager default");
        require("memory".equals(context.getNodeName()), "node name default");

        System.out.println("PASS MemoryToolContextTest");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
