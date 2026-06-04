/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * Memory tools factory.
 * <p>
 * Mirrors Python's {@code create_memory_tools} function in
 * {@code openjiuwen.harness.tools.memory}.
 */
public class MemoryTools {

    /**
     * Create all memory tools.
     * <p>
     * Mirrors Python's {@code create_memory_tools()} function.
     */
    public static List<Object> createMemoryTools(Object memoryContext, String language, String agentId) {
        List<Object> tools = new ArrayList<>();
        tools.add(new MemorySearchTool(language, agentId, memoryContext));
        tools.add(new MemoryGetTool(language, agentId, memoryContext));
        tools.add(new WriteMemoryTool(language, agentId, memoryContext));
        tools.add(new EditMemoryTool(language, agentId, memoryContext));
        tools.add(new com.openjiuwen.harness.tools.ReadMemoryTool(language, agentId, memoryContext, null));
        return tools;
    }

    /**
     * Create memory tools with default context.
     */
    public static List<Object> createMemoryTools() {
        return createMemoryTools(null, "en", null);
    }

    /**
     * Create write memory tool.
     */
    public static WriteMemoryTool createWriteMemoryTool(Object memoryContext, String language, String agentId) {
        return new WriteMemoryTool(language, agentId, memoryContext);
    }

    /**
     * Create read memory tool.
     */
    public static MemoryGetTool createReadMemoryTool(Object memoryContext, String language, String agentId) {
        return new MemoryGetTool(language, agentId, memoryContext);
    }

    /**
     * Create direct file read memory tool.
     */
    public static com.openjiuwen.harness.tools.ReadMemoryTool createReadMemoryFileTool(
            Object memoryContext, String language, String agentId) {
        return new com.openjiuwen.harness.tools.ReadMemoryTool(language, agentId, memoryContext, null);
    }

    /**
     * Create search memory tool.
     */
    public static MemorySearchTool createSearchMemoryTool(Object memoryContext, String language, String agentId) {
        return new MemorySearchTool(language, agentId, memoryContext);
    }

    /**
     * Create edit memory tool.
     */
    public static EditMemoryTool createEditMemoryTool(Object memoryContext, String language, String agentId) {
        return new EditMemoryTool(language, agentId, memoryContext);
    }
}
