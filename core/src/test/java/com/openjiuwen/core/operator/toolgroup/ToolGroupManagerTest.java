/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.toolgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

@Tag("issue196-w1")
@Tag("TC-TG-01")
class ToolGroupManagerTest {

    private final ToolGroupManager toolGroupManager = new ToolGroupManager();

    @Test
    void registerToolGroup_whenNameIsDuplicate_rejectsSecondGroup() {
        toolGroupManager.registerToolGroup(
                new ToolGroup("research", "Research", List.of("search")));

        assertThatThrownBy(
                        () ->
                                toolGroupManager.registerToolGroup(
                                        new ToolGroup("research", "Duplicate", List.of("browser"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate tool group name");
        assertThat(toolGroupManager.getGroups()).hasSize(1);
    }

    @Test
    void registerToolGroup_whenNameUsesReservedSkillPrefix_rejectsGroup() {
        assertThatThrownBy(
                        () ->
                                toolGroupManager.registerToolGroup(
                                        new ToolGroup(
                                                "skill:cleanup",
                                                "Reserved skill group",
                                                List.of("delete_file"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reserved prefix 'skill:'");
        assertThat(toolGroupManager.getGroups()).isEmpty();
    }

    @Test
    void registerSkillToolGroup_whenNameUsesSkillPrefix_replacesImplicitGroup() {
        toolGroupManager.registerSkillToolGroup(
                new ToolGroup("skill:cleanup", "Initial", List.of("delete_file")));
        ToolGroup updated =
                new ToolGroup("skill:cleanup", "Updated", List.of("delete_file", "cleanup"));

        toolGroupManager.registerSkillToolGroup(updated);

        assertThat(toolGroupManager.getToolGroup("skill:cleanup")).containsSame(updated);
        assertThat(toolGroupManager.getGroups()).containsExactly(updated);
    }

    @Test
    void groupRegistry_whenRegisteredAndUnregistered_exposesStableSnapshot() {
        ToolGroup researchGroup = new ToolGroup("research", null, List.of("search"));
        ToolGroup analysisGroup = new ToolGroup("analysis", "Analysis", List.of("chart"));
        toolGroupManager.registerToolGroup(researchGroup);
        toolGroupManager.registerToolGroup(analysisGroup);

        assertThat(toolGroupManager.getToolGroup(" research ")).containsSame(researchGroup);
        assertThat(toolGroupManager.getGroups()).containsExactly(researchGroup, analysisGroup);

        toolGroupManager.unregisterToolGroup("research");

        assertThat(toolGroupManager.getToolGroup("research")).isEmpty();
        assertThat(toolGroupManager.getGroups()).containsExactly(analysisGroup);
    }

    @Test
    @Tag("TC-TG-02")
    void getActiveToolNames_whenActiveGroupsAreEmptyOrUnknown_returnsOnlyUngroupedTools() {
        toolGroupManager.registerToolGroup(
                new ToolGroup("research", "Research", List.of("search")));

        assertThat(toolGroupManager.getActiveToolNames(List.of(), List.of("search", "clock")))
                .containsExactly("clock");
        assertThat(
                        toolGroupManager.getActiveToolNames(
                                List.of("missing"), List.of("search", "clock")))
                .containsExactly("clock");
    }

    @Test
    @Tag("TC-TG-03")
    void getActiveToolNames_whenSameToolIsInTwoGroups_returnsOneName() {
        toolGroupManager.registerToolGroup(
                new ToolGroup("research", "Research", List.of("search")));
        toolGroupManager.registerToolGroup(
                new ToolGroup("analysis", "Analysis", List.of("search", "chart")));

        assertThat(
                        toolGroupManager.getActiveToolNames(
                                List.of("research", "analysis"), List.of("search", "chart")))
                .containsExactly("chart", "search");
    }

    @Test
    void getActiveToolNames_whenNoGroupsExist_returnsAllRegisteredTools() {
        assertThat(
                        toolGroupManager.getActiveToolNames(
                                List.of("research"), List.of("search", "clock")))
                .containsExactly("clock", "search");
    }

    @Test
    void registerToolGroup_whenGroupIsEmpty_acceptsRegistration() {
        toolGroupManager.registerToolGroup(new ToolGroup("empty", "Empty group", List.of()));

        assertThat(toolGroupManager.getToolGroup("empty")).isPresent();
        assertThat(toolGroupManager.getActiveToolNames(List.of("empty"), List.of("clock")))
                .containsExactly("clock");
    }

    @Test
    void isToolEligibleForExecution_whenToolIsInActiveExplicitGroup_returnsTrue() {
        toolGroupManager.registerToolGroup(
                new ToolGroup("research", "Research", List.of("search")));

        assertThat(toolGroupManager.isToolEligibleForExecution("search", List.of("research")))
                .isTrue();
    }

    @Test
    void isToolEligibleForExecution_whenExplicitGroupIsInactive_skillGroupCannotOverride() {
        toolGroupManager.registerToolGroup(
                new ToolGroup("danger_tools", "Danger tools", List.of("delete_file")));

        assertThat(
                        toolGroupManager.isToolEligibleForExecution(
                                "delete_file", List.of("danger_tools")))
                .isTrue();
        assertThat(toolGroupManager.isToolEligibleForExecution("delete_file", List.of())).isFalse();
    }

    @Test
    void isToolEligibleForExecution_whenToolIsUngrouped_returnsTrue() {
        toolGroupManager.registerToolGroup(
                new ToolGroup("research", "Research", List.of("search")));

        assertThat(toolGroupManager.isToolEligibleForExecution("clock", List.of())).isTrue();
        assertThat(toolGroupManager.isToolEligibleForExecution("clock", List.of("research")))
                .isTrue();
    }

    @Test
    void isToolEligibleForExecution_whenToolNameIsUnknownOrBlank_returnsExpectedResult() {
        toolGroupManager.registerToolGroup(
                new ToolGroup("research", "Research", List.of("search")));

        assertThat(toolGroupManager.isToolEligibleForExecution("missing", List.of())).isTrue();
        assertThat(toolGroupManager.isToolEligibleForExecution("", List.of())).isFalse();
        assertThat(toolGroupManager.isToolEligibleForExecution("   ", List.of())).isFalse();
    }
}
