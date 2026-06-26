/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.memory.lite.CodingMemoryToolContext;
import com.openjiuwen.core.memory.lite.CodingMemoryToolOps;
import com.openjiuwen.core.memory.lite.MemorySettings;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Factory for harness runtime coding-memory tools.
 *
 * <p>Mirrors Python's {@code create_coding_memory_tools} in
 * {@code openjiuwen/harness/tools/coding_memory.py}.</p>
 */
public final class CodingMemoryTools {

    private CodingMemoryTools() {
    }

    public static List<Tool> createCodingMemoryTools(CodingMemoryToolContext ctx) {
        CodingMemoryToolContext context = ctx == null ? new CodingMemoryToolContext() : ctx;
        if (context.getWorkspace() != null) {
            Path codingMemoryDir = context.getWorkspace().getNodePath(CodingMemoryToolContext.DEFAULT_NODE_NAME);
            String resolvedDir = codingMemoryDir == null ? "" : codingMemoryDir.toString();
            context.setCodingMemoryDir(resolvedDir);
            if (context.getSettings() == null) {
                context.setSettings(MemorySettings.createMemorySettings(resolvedDir, Map.of()));
            }
        }
        context.setNodeName(CodingMemoryToolContext.DEFAULT_NODE_NAME);
        return List.of(
                new CodingMemoryReadTool(inputs -> CodingMemoryToolOps.codingMemoryReadWithContext(
                        context,
                        text(inputs.get("path")),
                        integer(inputs.get("offset")),
                        integer(inputs.get("limit"))
                ).toCompletableFuture().join()),
                new CodingMemoryWriteTool(inputs -> CodingMemoryToolOps.codingMemoryWriteWithContext(
                        context,
                        text(inputs.get("path")),
                        text(inputs.get("content"))
                ).toCompletableFuture().join()),
                new CodingMemoryEditTool(inputs -> CodingMemoryToolOps.codingMemoryEditWithContext(
                        context,
                        text(inputs.get("path")),
                        text(inputs.get("old_text")),
                        text(inputs.get("new_text"))
                ).toCompletableFuture().join())
        );
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
