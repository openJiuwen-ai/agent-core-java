/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.memory;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.*;

/**
 * Memory get tool — retrieves memory content by path.
 * <p>
 * Mirrors Python's {@code MemoryGetTool} in
 * {@code openjiuwen.harness.tools.memory}.
 */
public class MemoryGetTool extends AbstractHarnessTool {

    private final Object memoryContext;

    public MemoryGetTool(String language, String agentId, Object memoryContext) {
        super(buildCard(language, agentId), null);
        this.memoryContext = memoryContext;
    }

    private static ToolCard buildCard(String language, String agentId) {
        String id = "MemoryGetTool_" + (agentId != null ? agentId : UUID.randomUUID().toString().substring(0, 8));
        String desc = "cn".equals(language) ? "获取记忆内容。" : "Get memory content.";
        return toolCard(id, "memory_get", desc);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        String path = (String) inputs.get("path");
        if (path == null || path.isBlank()) {
            return new ToolOutput(false, null, "path is required");
        }

        Integer fromLine = inputs.containsKey("from_line") ?
            Integer.parseInt(inputs.get("from_line").toString()) : null;
        Integer lines = inputs.containsKey("lines") ?
            Integer.parseInt(inputs.get("lines").toString()) : null;

        Map<String, Object> result = invokeMemoryGet(path, fromLine, lines);

        return new ToolOutput(true, result, null);
    }

    private Map<String, Object> invokeMemoryGet(String path, Integer fromLine, Integer lines) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path);
        result.put("content", "");

        try {
            if (memoryContext instanceof com.openjiuwen.core.memory.lite.MemoryToolContext ctx) {
                return ctx.get(path, fromLine, lines);
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }

        return result;
    }
}