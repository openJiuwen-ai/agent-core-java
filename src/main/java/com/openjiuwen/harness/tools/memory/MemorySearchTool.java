/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.memory;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.*;

/**
 * Memory search tool — searches memory with embeddings.
 * <p>
 * Mirrors Python's {@code MemorySearchTool} in
 * {@code openjiuwen.harness.tools.memory}.
 */
public class MemorySearchTool extends AbstractHarnessTool {

    private final Object memoryContext;

    public MemorySearchTool(String language, String agentId, Object memoryContext) {
        super(buildCard(language, agentId), null);
        this.memoryContext = memoryContext;
    }

    private static ToolCard buildCard(String language, String agentId) {
        String id = "MemorySearchTool_" + (agentId != null ? agentId : UUID.randomUUID().toString().substring(0, 8));
        String desc = "cn".equals(language) ? "搜索记忆内容。" : "Search memory content.";
        return toolCard(id, "memory_search", desc);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        String query = (String) inputs.get("query");
        if (query == null || query.isBlank()) {
            return new ToolOutput(false, null, "query is required");
        }

        Integer maxResults = inputs.containsKey("max_results") ? 
            Integer.parseInt(inputs.get("max_results").toString()) : 10;
        Double minScore = inputs.containsKey("min_score") ?
            Double.parseDouble(inputs.get("min_score").toString()) : 0.5;

        // Invoke memory search via context
        Map<String, Object> result = invokeMemorySearch(query, maxResults, minScore);

        return new ToolOutput(true, result, null);
    }

    private Map<String, Object> invokeMemorySearch(String query, int maxResults, double minScore) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("max_results", maxResults);
        result.put("min_score", minScore);
        result.put("results", new ArrayList<>());

        // Try to use memory context if available
        try {
            if (memoryContext instanceof com.openjiuwen.core.memory.lite.MemoryToolContext ctx) {
                return ctx.search(query, maxResults, minScore);
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }

        return result;
    }
}