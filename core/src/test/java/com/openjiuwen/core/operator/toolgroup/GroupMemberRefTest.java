/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.toolgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests immutable GroupMemberRef behavior.
 *
 * @since 0.1.19
 */
class GroupMemberRefTest {

    @Test
    void groupMemberRef_whenValid_returnsTypeAndId() {
        GroupMemberRef ref = new GroupMemberRef(GroupMemberType.TOOL, "bash");

        assertThat(ref.type()).isEqualTo(GroupMemberType.TOOL);
        assertThat(ref.id()).isEqualTo("bash");
    }

    @Test
    void groupMemberRef_whenSkillType_returnsTypeAndId() {
        GroupMemberRef ref = new GroupMemberRef(GroupMemberType.SKILL, "git-helper");

        assertThat(ref.type()).isEqualTo(GroupMemberType.SKILL);
        assertThat(ref.id()).isEqualTo("git-helper");
    }

    @Test
    void groupMemberRef_whenTypeIsNull_rejectsIt() {
        assertThatThrownBy(() -> new GroupMemberRef(null, "bash"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Group member type must not be null");
    }

    @Test
    void groupMemberRef_whenIdIsNull_rejectsIt() {
        assertThatThrownBy(() -> new GroupMemberRef(GroupMemberType.TOOL, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Group member id must not be blank");
    }

    @Test
    void groupMemberRef_whenIdIsBlank_rejectsIt() {
        assertThatThrownBy(() -> new GroupMemberRef(GroupMemberType.TOOL, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Group member id must not be blank");
    }

    @Test
    void groupMemberRef_whenIdHasLeadingOrTrailingWhitespace_rejectsIt() {
        assertThatThrownBy(() -> new GroupMemberRef(GroupMemberType.TOOL, " bash "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leading or trailing whitespace");
    }

    @Test
    void groupMemberRef_whenSameTypeAndId_isEqual() {
        GroupMemberRef first = new GroupMemberRef(GroupMemberType.TOOL, "bash");
        GroupMemberRef second = new GroupMemberRef(GroupMemberType.TOOL, "bash");

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    void groupMemberRef_whenTypeOrIdDiffers_isNotEqual() {
        GroupMemberRef toolRef = new GroupMemberRef(GroupMemberType.TOOL, "bash");
        GroupMemberRef skillRef = new GroupMemberRef(GroupMemberType.SKILL, "bash");

        assertThat(toolRef).isNotEqualTo(skillRef);
    }
}
