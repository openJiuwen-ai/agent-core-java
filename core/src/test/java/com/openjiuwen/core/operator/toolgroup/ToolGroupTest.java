/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.toolgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Tests immutable ToolGroup behavior.
 *
 * @since 0.1.17
 */
class ToolGroupTest {
    @Test
    void toolGroup_whenInputCollectionChanges_keepsInternalStateUnchanged() {
        List<String> toolNames = new ArrayList<>(List.of("search", "calculator"));
        ToolGroup toolGroup = ToolGroup.ofToolNames("research", "Research tools", toolNames);

        toolNames.add("browser");

        assertThat(toolGroup.toolNames()).containsExactly("search", "calculator");
        assertThatThrownBy(() -> toolGroup.toolNames().add("browser"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toolGroup_whenNameIsBlank_rejectsIt() {
        assertThatThrownBy(() -> ToolGroup.ofToolNames(" ", "Invalid", List.of("search")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toolGroup_whenToolNameIsBlank_rejectsIt() {
        assertThatThrownBy(
                        () -> ToolGroup.ofToolNames("research", "Research tools", List.of("search", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tool name must not be blank");
    }

    @Test
    void toolGroup_whenToolNameHasLeadingOrTrailingWhitespace_rejectsIt() {
        assertThatThrownBy(() -> ToolGroup.ofToolNames("research", "Research tools", List.of(" search ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leading or trailing whitespace");
    }

    @Test
    void toolGroup_whenDescriptionIsBlank_acceptsIt() {
        ToolGroup toolGroup = ToolGroup.ofToolNames("research", "", List.of("search"));

        assertThat(toolGroup.name()).isEqualTo("research");
        assertThat(toolGroup.description()).isEmpty();
        assertThat(toolGroup.toolNames()).containsExactly("search");
    }

    @Test
    void toolGroup_ofToolNames_convertsToToolMembers() {
        ToolGroup group = ToolGroup.ofToolNames("research", "Research tools", List.of("bash", "grep"));

        assertThat(group.members()).hasSize(2);
        assertThat(group.members()).allSatisfy(m -> assertThat(m.type()).isEqualTo(GroupMemberType.TOOL));
        assertThat(group.toolNames()).containsExactly("bash", "grep");
        assertThat(group.skillNames()).isEmpty();
    }

    @Test
    void toolGroup_canonicalConstructor_acceptsMixedMembers() {
        ToolGroup group = new ToolGroup("research", "Research", List.of(
                new GroupMemberRef(GroupMemberType.TOOL, "bash"),
                new GroupMemberRef(GroupMemberType.SKILL, "git-helper")));

        assertThat(group.toolNames()).containsExactly("bash");
        assertThat(group.skillNames()).containsExactly("git-helper");
    }

    @Test
    void toolGroup_whenDuplicateMembers_deduplicatesPreservingOrder() {
        ToolGroup group = ToolGroup.ofToolNames("research", "Research", List.of("bash", "bash", "grep"));

        assertThat(group.toolNames()).containsExactly("bash", "grep");
    }

    @Test
    void toolGroup_whenCanonicalMemberIdHasWhitespace_rejectsIt() {
        assertThatThrownBy(
                        () -> new ToolGroup("research", "Research", List.of(
                                new GroupMemberRef(GroupMemberType.TOOL, " bash"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leading or trailing whitespace");
    }

    @Test
    void toolGroup_whenMembersIsNull_rejectsIt() {
        assertThatThrownBy(() -> new ToolGroup("research", "Research", (Collection<GroupMemberRef>) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("members must not be null");
    }
}
