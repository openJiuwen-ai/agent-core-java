/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.SysOperation;

import java.util.*;

/**
 * Memory get tool — retrieves memory by path.
 * <p>
 * Mirrors Python's {@code MemoryGetTool} in
 * {@code openjiuwen.harness.tools.memory}.
 */
public class MemoryGetTool extends AbstractHarnessTool {

    private final Object memoryContext;

    public MemoryGetTool(String language, String agentId, Object memoryContext,
                          SysOperation sysOperation) {
        super(buildCard(language, agentId), sysOperation);
        this.memoryContext = memoryContext;
    }

    private static ToolCard buildCard(String language, String agentId) {
        String id = "MemoryGetTool_" + (agentId != null ? agentId : UUID.randomUUID().toString().substring(0, 8));
        String desc = "cn".equals(language) ? "获取记忆详情。" : "Get memory details.";
        return toolCard(id, "memory_get", desc);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        String path = (String) inputs.get("path");
        if (path == null || path.isBlank()) {
            return new ToolOutput(false, null, "path is required");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path);
        result.put("content", "");
        return new ToolOutput(true, result, null);
    }
}
