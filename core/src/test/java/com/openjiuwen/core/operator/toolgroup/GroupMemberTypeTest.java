/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.toolgroup;

import org.junit.jupiter.api.Test;

/**
 * Verifies that GroupMemberType exposes the expected constants.
 *
 * @since 0.1.19
 */
class GroupMemberTypeTest {

    @Test
    void groupMemberType_containsToolAndSkill() {
        org.assertj.core.api.Assertions.assertThat(GroupMemberType.values())
                .containsExactlyInAnyOrder(GroupMemberType.TOOL, GroupMemberType.SKILL);
    }
}
