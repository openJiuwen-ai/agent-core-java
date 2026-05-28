/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.SysOperation;

import java.util.*;

/**
 * Harness memory tools: search, get, read, write, edit.
 * <p>
 * Uses existing Java memory infrastructure (BaseMemoryManager, SearchManager, etc.)
 * instead of Python's lite memory_tool_ops.
 * <p>
 * Mirrors Python's memory tools in {@code openjiuwen.harness.tools.memory}.
 */
public class MemorySearchTool extends AbstractHarnessTool {

    private final Object memoryContext;

    public MemorySearchTool(String language, String agentId, Object memoryContext,
                             SysOperation sysOperation) {
        super(buildCard(language, agentId), sysOperation);
        this.memoryContext = memoryContext;
    }

    private static ToolCard buildCard(String language, String agentId) {
        String id = "MemorySearchTool_" + (agentId != null ? agentId : UUID.randomUUID().toString().substring(0, 8));
        String desc = "cn".equals(language)
                ? "搜索记忆内容。"
                : "Search memory content.";
        return toolCard(id, "memory_search", desc);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        String query = (String) inputs.get("query");
        if (query == null || query.isBlank()) {
            return new ToolOutput(false, null, "query is required");
        }
        // Delegate to memory infrastructure
        // For now, return a placeholder until memory lite is available
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("results", Collections.emptyList());
        return new ToolOutput(true, result, null);
    }
}
