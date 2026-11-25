/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.toolgroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable named group of tool and skill member references.
 *
 * <p>Members are stored as {@link GroupMemberRef} instances so that a group
 * can activate plain tools and skill-bound capabilities together. The
 * canonical accessor is {@link #members()}; {@link #toolNames()} and
 * {@link #skillNames()} are derived views over TOOL and SKILL members
 * respectively.
 *
 * <p>Use {@link #ofToolNames(String, String, Collection)} to create a group
 * from plain tool names (the 0.1.17 API shape); the canonical constructor
 * accepts {@link GroupMemberRef} instances.
 *
 * @param name unique group name
 * @param description optional human-readable description
 * @param members ordered member references in this group
 * @since 0.1.17
 */
public record ToolGroup(String name, String description, Collection<GroupMemberRef> members) {

    /**
     * Creates and validates an immutable tool-group snapshot.
     *
     * @param name unique group name
     * @param description optional human-readable description
     * @param members ordered member references in this group
     * @throws IllegalArgumentException when name is invalid or members are invalid
     */
    public ToolGroup {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool group name must not be blank");
        }
        name = name.trim();
        if (members == null) {
            throw new IllegalArgumentException("Tool group members must not be null");
        }
        Set<GroupMemberRef> normalizedMembers = new LinkedHashSet<>(members);
        members = Collections.unmodifiableSet(normalizedMembers);
    }

    /**
     * Creates a group from plain tool names, preserving the common
     * {@code List.of} call shape from the 0.1.17 API.
     *
     * <p>This overload delegates to {@link #ofToolNames(String, String,
     * Collection)} so callers that have not migrated to {@link GroupMemberRef}
     * continue to compile.
     *
     * @param name unique group name
     * @param description optional human-readable description
     * @param toolNames ordered tool names in this group
     * @throws IllegalArgumentException when name or tool names are invalid
     * @since 0.1.19
     * @deprecated Use the canonical constructor with
     *     {@link GroupMemberRef} instances.
     */
    @Deprecated(since = "0.1.19")
    public ToolGroup(String name, String description, List<String> toolNames) {
        this(name, description, toToolMembers(name, toolNames));
    }

    /**
     * Creates a group from plain tool names, converting each name to a
     * {@link GroupMemberRef} of type {@link GroupMemberType#TOOL}.
     *
     * <p>This factory preserves the 0.1.17 API shape for callers that have
     * not yet migrated to {@link GroupMemberRef}.
     *
     * @param name unique group name
     * @param description optional human-readable description
     * @param toolNames ordered tool names in this group
     * @return validated immutable tool group
     * @throws IllegalArgumentException when name or tool names are invalid
     * @since 0.1.19
     * @deprecated Use the canonical constructor with
     *     {@link GroupMemberRef} instances.
     */
    @Deprecated(since = "0.1.19")
    public static ToolGroup ofToolNames(
            String name, String description, Collection<String> toolNames) {
        return new ToolGroup(name, description, toToolMembers(name, toolNames));
    }

    /**
     * Returns the ids of TOOL-type members in this group.
     *
     * <p>This is a derived view over {@link #members()}; it never persists
     * state and must not be used as an independent data source.
     *
     * @return unmodifiable ordered list of tool names in this group
     * @since 0.1.19
     * @deprecated Iterate {@link #members()} and filter
     *     {@link GroupMemberType#TOOL} instead.
     */
    @Deprecated(since = "0.1.19")
    public List<String> toolNames() {
        return members.stream()
                .filter(member -> member.type() == GroupMemberType.TOOL)
                .map(GroupMemberRef::id)
                .toList();
    }

    /**
     * Returns the ids of SKILL-type members in this group.
     *
     * @return unmodifiable ordered list of skill names in this group
     * @since 0.1.19
     */
    public List<String> skillNames() {
        return members.stream()
                .filter(member -> member.type() == GroupMemberType.SKILL)
                .map(GroupMemberRef::id)
                .toList();
    }

    private static Collection<GroupMemberRef> toToolMembers(
            String groupName, Collection<String> toolNames) {
        if (toolNames == null) {
            throw new IllegalArgumentException("Tool group tool names must not be null");
        }
        List<GroupMemberRef> converted = new ArrayList<>(toolNames.size());
        for (String toolName : toolNames) {
            if (toolName == null || toolName.isBlank()) {
                throw new IllegalArgumentException(
                        "Tool name must not be blank in group: " + groupName);
            }
            if (!toolName.equals(toolName.trim())) {
                throw new IllegalArgumentException(
                        "Tool name must not contain leading or trailing whitespace in group: "
                                + groupName);
            }
            converted.add(new GroupMemberRef(GroupMemberType.TOOL, toolName));
        }
        return converted;
    }
}
