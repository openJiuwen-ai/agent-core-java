/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Builtin tool and rail group registry for HarnessConfigBuilder.
 * <p>
 * Mirrors Python's {@code _BUILTIN_TOOL_GROUPS} and {@code _BUILTIN_RAIL_REGISTRY}
 * in {@code openjiuwen.harness.harness_config.builder}.
 */
final class BuiltinRegistries {

    private BuiltinRegistries() {}

    /** Builtin tool group entries: group -> (module hint, class names, needs sys_operation). */
    static final Map<String, ToolGroupEntry> BUILTIN_TOOL_GROUPS = new HashMap<>();

    /** Builtin rail registry: rail name -> class hint. */
    static final Map<String, String> BUILTIN_RAIL_REGISTRY = new HashMap<>();

    static {
        BUILTIN_TOOL_GROUPS.put("filesystem", ToolGroupEntry.of(
                "com.openjiuwen.harness.tools",
                new String[]{"ReadFileTool", "WriteFileTool", "EditFileTool", "ListDirTool", "GlobTool", "GrepTool"},
                true));
        BUILTIN_TOOL_GROUPS.put("shell", ToolGroupEntry.of(
                "com.openjiuwen.harness.tools",
                new String[]{"BashTool"},
                true));
        BUILTIN_TOOL_GROUPS.put("code", ToolGroupEntry.of(
                "com.openjiuwen.harness.tools",
                new String[]{"CodeTool"},
                true));
        BUILTIN_TOOL_GROUPS.put("web_search", ToolGroupEntry.of(
                "com.openjiuwen.harness.tools",
                new String[]{"WebFreeSearchTool", "WebPaidSearchTool"},
                false));
        BUILTIN_TOOL_GROUPS.put("web_fetch", ToolGroupEntry.of(
                "com.openjiuwen.harness.tools",
                new String[]{"WebFetchWebpageTool"},
                false));

        BUILTIN_RAIL_REGISTRY.put("task_planning", "com.openjiuwen.harness.rails.TaskPlanningRail");
    }

    record ToolGroupEntry(String moduleHint, String[] classNames, boolean needsSysOperation) {
        static ToolGroupEntry of(String moduleHint, String[] classNames, boolean needsSysOperation) {
            return new ToolGroupEntry(moduleHint, classNames, needsSysOperation);
        }
    }
}
