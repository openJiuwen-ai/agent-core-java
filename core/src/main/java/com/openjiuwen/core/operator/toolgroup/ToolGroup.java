/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.toolgroup;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Immutable named group of tool names.
 *
 * @param name unique group name
 * @param description optional human-readable description
 * @param toolNames ordered tool names in this group
 * @since 0.1.17
 */
public record ToolGroup(String name, String description, Collection<String> toolNames) {

    private static final String SKILL_GROUP_PREFIX = "skill:";

    /** Creates and validates an immutable tool-group snapshot. */
    public ToolGroup {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool group name must not be blank");
        }
        name = name.trim();
        if (toolNames == null) {
            throw new IllegalArgumentException("Tool group tool names must not be null");
        }
        Set<String> normalizedToolNames = new LinkedHashSet<>();
        for (String toolName : toolNames) {
            if (toolName == null || toolName.isBlank()) {
                throw new IllegalArgumentException("Tool name must not be blank in group: " + name);
            }
            if (!toolName.equals(toolName.trim())) {
                throw new IllegalArgumentException(
                        "Tool name must not contain leading or trailing whitespace in group: "
                                + name);
            }
            normalizedToolNames.add(toolName);
        }
        toolNames = Collections.unmodifiableSet(normalizedToolNames);
    }
}
