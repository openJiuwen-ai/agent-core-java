/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.toolgroup;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared factory and collection helpers for ToolGroup test cases. */
public final class ToolGroupTestSupport {

    private static final Map<String, Object> EMPTY_PARAMETERS = Map.of();

    private ToolGroupTestSupport() {}

    public static ToolInfo toolInfo(String name, String description) {
        return toolInfo(name, description, EMPTY_PARAMETERS);
    }

    public static ToolInfo toolInfo(
            String name, String description, Map<String, Object> parameters) {
        return ToolInfo.builder()
                .type("function")
                .name(name)
                .description(description)
                .parameters(parameters)
                .build();
    }

    public static List<ToolInfo> toolInfos(String... toolNames) {
        return List.of(toolNames).stream()
                .map(toolName -> toolInfo(toolName, "Tool " + toolName))
                .toList();
    }

    public static Set<String> toolNames(Collection<ToolInfo> toolInfos) {
        return toolInfos.stream()
                .map(ToolInfo::getName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public static List<String> sortedToolNames(Collection<String> toolNames) {
        return toolNames.stream().distinct().sorted().toList();
    }
}
