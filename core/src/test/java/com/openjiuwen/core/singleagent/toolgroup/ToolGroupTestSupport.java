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

    /**
     * Creates a function ToolInfo with empty parameters.
     *
     * @param name tool name
     * @param description tool description
     * @return populated ToolInfo
     */
    public static ToolInfo toolInfo(String name, String description) {
        return toolInfo(name, description, EMPTY_PARAMETERS);
    }

    /**
     * Creates a function ToolInfo with the supplied parameters.
     *
     * @param name tool name
     * @param description tool description
     * @param parameters JSON-schema parameters
     * @return populated ToolInfo
     */
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
     * Creates ToolInfo instances for the supplied tool names.
     *
     * @param toolNames ordered tool names
     * @return ordered ToolInfo instances
     */
    public static List<ToolInfo> toolInfos(String... toolNames) {
        return List.of(toolNames).stream()
                .map(toolName -> toolInfo(toolName, "Tool " + toolName))
                .toList();
    }

    /**
     * Extracts unique tool names while preserving input order.
     *
     * @param toolInfos tool metadata to inspect
     * @return ordered tool-name set
     */
    public static Set<String> toolNames(Collection<ToolInfo> toolInfos) {
        return toolInfos.stream()
                .map(ToolInfo::getName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Returns unique tool names in lexical order.
     *
     * @param toolNames tool names to normalize
     * @return sorted unique tool names
     */
    public static List<String> sortedToolNames(Collection<String> toolNames) {
        return toolNames.stream().distinct().sorted().toList();
    }
}
