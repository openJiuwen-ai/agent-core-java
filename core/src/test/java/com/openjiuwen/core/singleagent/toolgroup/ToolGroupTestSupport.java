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

/**
 * Shared factory and collection helpers for ToolGroup test cases.
 *
 * @since 0.1.17
 */
public final class ToolGroupTestSupport {
    private static final Map<String, Object> EMPTY_PARAMETERS = Map.of();

    private ToolGroupTestSupport() {}

    /**
     * Creates a function tool description without parameters.
     *
     * @param name tool name
     * @param description tool description
     * @return new tool description
     */
    /**
     * Creates a function tool description with explicit parameters.
     *
     * @param name tool name
     * @param description tool description
     * @param parameters tool parameter schema
     * @return new tool description
     */
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

    /**
     * Creates tool descriptions for the supplied names.
     *
     * @param toolNames names of the tools to create
     * @return immutable list of tool descriptions
     */
    public static List<ToolInfo> toolInfos(String... toolNames) {
        return List.of(toolNames).stream()
                .map(toolName -> toolInfo(toolName, "Tool " + toolName))
                .toList();
    }

    /**
     * Extracts tool names while preserving iteration order.
     *
     * @param toolInfos tool descriptions to inspect
     * @return ordered set of tool names
     */
    public static Set<String> toolNames(Collection<ToolInfo> toolInfos) {
        return toolInfos.stream()
                .map(ToolInfo::getName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Returns distinct tool names in sorted order.
     *
     * @param toolNames tool names to normalize
     * @return immutable sorted name list
     */
    public static List<String> sortedToolNames(Collection<String> toolNames) {
        return toolNames.stream().distinct().sorted().toList();
    }
}
