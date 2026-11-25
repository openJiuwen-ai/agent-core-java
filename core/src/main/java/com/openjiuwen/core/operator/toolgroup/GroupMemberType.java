/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.toolgroup;

/**
 * Type of a member referenced by a {@link ToolGroup}.
 *
 * <p>A tool group may hold references to plain tools and to skills whose
 * bound tools should be activated together with the group.
 *
 * @since 0.1.19
 */
public enum GroupMemberType {
    /** A plain tool referenced by its registered tool name. */
    TOOL,

    /** A skill referenced by its registered skill name. */
    SKILL
}
