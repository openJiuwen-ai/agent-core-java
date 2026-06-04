/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.memory.lite.MemoryToolContext;
import com.openjiuwen.core.memory.lite.MemoryToolOps;
import com.openjiuwen.core.sysop.SysOperation;

import java.util.*;

/**
 * Read memory tool — reads memory file content.
 * <p>
 * Mirrors Python's {@code ReadMemoryTool} in
 * {@code openjiuwen.harness.tools.memory}.
 */
public class ReadMemoryTool extends AbstractHarnessTool {

    private final Object memoryContext;

    public ReadMemoryTool(String language, String agentId, Object memoryContext,
                           SysOperation sysOperation) {
        super(buildCard(language, agentId), sysOperation);
        this.memoryContext = memoryContext;
    }

    public ReadMemoryTool(String language, String agentId, Object memoryContext) {
        this(language, agentId, memoryContext, null);
    }

    private static ToolCard buildCard(String language, String agentId) {
        String id = "ReadMemoryTool_" + (agentId != null ? agentId : UUID.randomUUID().toString().substring(0, 8));
        String desc = "cn".equals(language) ? "读取记忆内容。" : "Read memory content.";
        return toolCard(id, "read_memory", desc);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        String path = (String) inputs.get("path");
        if (path == null || path.isBlank()) {
            return new ToolOutput(false, null, "path is required");
        }
        Integer offset = parseInteger(inputs.get("offset"));
        Integer limit = parseInteger(inputs.get("limit"));

        Map<String, Object> result;
        if (memoryContext instanceof MemoryToolContext ctx) {
            result = MemoryToolOps.readMemoryWithContext(ctx, path, offset, limit).join();
        } else {
            result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("path", path);
            result.put("content", "");
            result.put("error", "Memory context not available");
        }
        return new ToolOutput(Boolean.TRUE.equals(result.get("success")), result, null);
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
