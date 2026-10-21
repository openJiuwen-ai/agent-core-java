/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.toolgroup;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link ToolInfo} registry used by ToolGroup tests. */
public final class FakeToolInfoRegistry {
    private final Map<String, ToolInfo> toolInfosByName = new ConcurrentHashMap<>();

    /**
     * Registers a defensive copy of a tool under its name.
     *
     * @param toolInfo tool metadata to register
     * @return independent copy of the registered tool
     */
    public ToolInfo register(ToolInfo toolInfo) {
        ToolInfo storedToolInfo = copyToolInfo(toolInfo);
        ToolInfo existingToolInfo =
                toolInfosByName.putIfAbsent(storedToolInfo.getName(), storedToolInfo);
        if (existingToolInfo != null) {
            throw new IllegalArgumentException("Duplicate tool name: " + storedToolInfo.getName());
        }
        return copyToolInfo(storedToolInfo);
    }

    /**
     * Removes a tool by name.
     *
     * @param toolName name of the tool to remove
     * @return true when a tool was removed
     */
    public boolean unregister(String toolName) {
        return toolInfosByName.remove(toolName) != null;
    }

    /** Removes every registered tool. */
    public void clear() {
        toolInfosByName.clear();
    }

    /**
     * Lists registered tools sorted by name.
     *
     * @return independent tool copies
     */
    public List<ToolInfo> listToolInfos() {
        return toolInfosByName.values().stream()
                .sorted(Comparator.comparing(ToolInfo::getName))
                .map(FakeToolInfoRegistry::copyToolInfo)
                .toList();
    }

    /**
     * Lists registered tool names in lexical order.
     *
     * @return sorted tool names
     */
    public List<String> listToolNames() {
        return toolInfosByName.values().stream().map(ToolInfo::getName).sorted().toList();
    }

    /**
     * Finds an independent copy of a registered tool.
     *
     * @param toolName tool name to find
     * @return matching ToolInfo, or empty when not found
     */
    public Optional<ToolInfo> findToolInfo(String toolName) {
        return Optional.ofNullable(toolInfosByName.get(toolName))
                .map(FakeToolInfoRegistry::copyToolInfo);
    }

    /**
     * Checks whether a tool name is registered.
     *
     * @param toolName tool name to check
     * @return true when the name is registered
     */
    public boolean containsToolName(String toolName) {
        return toolInfosByName.containsKey(toolName);
    }

    /**
     * Returns the number of registered tools.
     *
     * @return registered tool count
     */
    public int toolCount() {
        return toolInfosByName.size();
    }

    private static ToolInfo copyToolInfo(ToolInfo toolInfo) {
        if (toolInfo == null || toolInfo.getName() == null || toolInfo.getName().isBlank()) {
            throw new IllegalArgumentException("Tool name must not be blank");
        }
        Map<String, Object> parameters =
                toolInfo.getParameters() == null ? Map.of() : toolInfo.getParameters();
        return ToolInfo.builder()
                .type(toolInfo.getType())
                .name(toolInfo.getName())
                .description(toolInfo.getDescription())
                .parameters(parameters)
                .build();
    }
}
