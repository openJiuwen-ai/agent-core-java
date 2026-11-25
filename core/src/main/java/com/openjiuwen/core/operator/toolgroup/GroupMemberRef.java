/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.toolgroup;

import java.util.Objects;

/**
 * Immutable reference to one member of a {@link ToolGroup}.
 *
 * <p>The {@code id} is the registered name of the referenced entity: a tool
 * name for {@link GroupMemberType#TOOL} members, a skill name for
 * {@link GroupMemberType#SKILL} members.
 *
 * @param type member type; must not be null
 * @param id registered name of the referenced entity; must not be blank
 * @since 0.1.19
 */
public record GroupMemberRef(GroupMemberType type, String id) {

    /**
     * Creates and validates an immutable member reference.
     *
     * @param type member type; must not be null
     * @param id registered name of the referenced entity; must not be blank
     * @throws IllegalArgumentException when type is null or id is blank
     */
    public GroupMemberRef {
        Objects.requireNonNull(type, "Group member type must not be null");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Group member id must not be blank");
        }
        if (!id.equals(id.trim())) {
            throw new IllegalArgumentException(
                    "Group member id must not contain leading or trailing whitespace");
        }
    }
}
