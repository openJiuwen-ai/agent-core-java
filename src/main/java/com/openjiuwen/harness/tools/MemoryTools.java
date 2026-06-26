/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.memory.lite.MemorySettings;
import com.openjiuwen.core.memory.lite.MemoryToolContext;
import com.openjiuwen.core.memory.lite.MemoryToolOps;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Factory for harness runtime memory tools.
 *
 * <p>Mirrors Python's {@code create_memory_tools} in
 * {@code openjiuwen/harness/tools/memory.py}.</p>
 */
public final class MemoryTools {

    private MemoryTools() {
    }

    public static List<Tool> createMemoryTools(MemoryToolContext ctx) {
        MemoryToolContext context = ctx == null ? new MemoryToolContext() : ctx;
        if (context.getSettings() == null && context.getWorkspace() != null) {
            Path memoryDir = context.getWorkspace().getNodePath("memory");
            context.setSettings(MemorySettings.createMemorySettings(
                    memoryDir == null ? "" : memoryDir.toString(),
                    Map.of()
            ));
        }
        if (context.getNodeName() == null || context.getNodeName().isBlank()) {
            context.setNodeName("memory");
        }
        return List.of(
                new MemorySearchTool(inputs -> MemoryToolOps.memorySearchWithContext(
                        context,
                        text(inputs.get("query")),
                        integer(inputs.get("max_results")),
                        decimal(inputs.get("min_score")),
                        nullableText(inputs.get("session_key"))
                ).toCompletableFuture().join()),
                new MemoryGetTool(inputs -> MemoryToolOps.memoryGetWithContext(
                        context,
                        text(inputs.get("path")),
                        integer(inputs.get("from_line")),
                        integer(inputs.get("lines"))
                ).toCompletableFuture().join()),
                new WriteMemoryTool(inputs -> MemoryToolOps.writeMemoryWithContext(
                        context,
                        text(inputs.get("path")),
                        text(inputs.get("content")),
                        bool(inputs.get("append"), false)
                ).toCompletableFuture().join()),
                new EditMemoryTool(inputs -> MemoryToolOps.editMemoryWithContext(
                        context,
                        text(inputs.get("path")),
                        text(inputs.get("old_text")),
                        text(inputs.get("new_text"))
                ).toCompletableFuture().join()),
                new ReadMemoryTool(inputs -> MemoryToolOps.readMemoryWithContext(
                        context,
                        text(inputs.get("path")),
                        integer(inputs.get("offset")),
                        integer(inputs.get("limit"))
                ).toCompletableFuture().join())
        );
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String nullableText(Object value) {
        return value == null ? null : String.valueOf(value);
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

    private static Double decimal(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static boolean bool(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return List.of("1", "true", "yes", "y", "on").contains(String.valueOf(value).toLowerCase());
    }
}
