/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.memory;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.*;

/**
 * Write memory tool — writes or appends to memory files.
 * <p>
 * Mirrors Python's {@code WriteMemoryTool} in
 * {@code openjiuwen.harness.tools.memory}.
 */
public class WriteMemoryTool extends AbstractHarnessTool {

    private final Object memoryContext;

    public WriteMemoryTool(String language, String agentId, Object memoryContext) {
        super(buildCard(language, agentId), null);
        this.memoryContext = memoryContext;
    }

    private static ToolCard buildCard(String language, String agentId) {
        String id = "WriteMemoryTool_" + (agentId != null ? agentId : UUID.randomUUID().toString().substring(0, 8));
        String desc = "cn".equals(language) ? "写入记忆内容。" : "Write memory content.";
        return toolCard(id, "write_memory", desc);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        String path = (String) inputs.get("path");
        if (path == null || path.isBlank()) {
            return new ToolOutput(false, null, "path is required");
        }
        String content = (String) inputs.get("content");
        if (content == null) {
            return new ToolOutput(false, null, "content is required");
        }
        boolean append = Boolean.TRUE.equals(inputs.get("append"));

        Map<String, Object> result = invokeMemoryWrite(path, content, append);
        return new ToolOutput(true, result, null);
    }

    private Map<String, Object> invokeMemoryWrite(String path, String content, boolean append) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path);
        result.put("append", append);
        result.put("success", true);

        try {
            if (memoryContext instanceof com.openjiuwen.core.memory.lite.MemoryToolContext ctx) {
                ctx.write(path, content, append);
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("success", false);
        }

        return result;
    }
}