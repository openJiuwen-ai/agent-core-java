/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.toolgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests ToolGroupManager registration.
 *
 * @since 0.1.17
 */
class ToolGroupManagerTest {
    private final ToolGroupManager toolGroupManager = new ToolGroupManager();

    @Test
    void registerToolGroup_whenNameIsDuplicate_rejectsSecondGroup() {
        toolGroupManager.registerToolGroup(
                ToolGroup.ofToolNames("research", "Research", List.of("search")));

        assertThatThrownBy(
                        () ->
                                toolGroupManager.registerToolGroup(
                                        ToolGroup.ofToolNames("research", "Duplicate", List.of("browser"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate tool group name");
        assertThat(toolGroupManager.getGroups()).hasSize(1);
    }

    @Test
    void registerToolGroup_whenNameUsesReservedSkillPrefix_rejectsGroup() {
        assertThatThrownBy(
                        () ->
                                toolGroupManager.registerToolGroup(
                                        ToolGroup.ofToolNames(
                                                "skill:cleanup",
                                                "Reserved skill group",
                                                List.of("delete_file"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reserved prefix 'skill:'");
        assertThat(toolGroupManager.getGroups()).isEmpty();
    }

    @Test
    void registerSkillToolGroup_skillPrefix_replacesImplicitGroup() {
        toolGroupManager.registerSkillToolGroup(
                ToolGroup.ofToolNames("skill:cleanup", "Initial", List.of("delete_file")));
        ToolGroup updated =
                ToolGroup.ofToolNames("skill:cleanup", "Updated", List.of("delete_file", "cleanup"));

        toolGroupManager.registerSkillToolGroup(updated);

        assertThat(toolGroupManager.getToolGroup("skill:cleanup")).containsSame(updated);
        assertThat(toolGroupManager.getGroups()).containsExactly(updated);
    }

    @Test
    void groupRegistry_afterUnregister_exposesStableSnapshot() {
        ToolGroup researchGroup = ToolGroup.ofToolNames("research", null, List.of("search"));
        ToolGroup analysisGroup = ToolGroup.ofToolNames("analysis", "Analysis", List.of("chart"));
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
    void getActiveToolNames_forEmptyOrUnknownGroups_returnsUngroupedTools() {
        toolGroupManager.registerToolGroup(
                ToolGroup.ofToolNames("research", "Research", List.of("search")));

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
                ToolGroup.ofToolNames("research", "Research", List.of("search")));
        toolGroupManager.registerToolGroup(
                ToolGroup.ofToolNames("analysis", "Analysis", List.of("search", "chart")));

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
        toolGroupManager.registerToolGroup(ToolGroup.ofToolNames("empty", "Empty group", List.of()));

        assertThat(toolGroupManager.getToolGroup("empty")).isPresent();
        assertThat(toolGroupManager.getActiveToolNames(List.of("empty"), List.of("clock")))
                .containsExactly("clock");
    }

    @Test
    void isToolEligibleForExecution_inActiveExplicitGroup_returnsTrue() {
        toolGroupManager.registerToolGroup(
                ToolGroup.ofToolNames("research", "Research", List.of("search")));

        assertThat(toolGroupManager.isToolEligibleForExecution("search", List.of("research")))
                .isTrue();
    }

    @Test
    void isToolEligibleForExecution_inactiveGroup_skillCannotOverride() {
        toolGroupManager.registerToolGroup(
                ToolGroup.ofToolNames("danger_tools", "Danger tools", List.of("delete_file")));

        assertThat(
                        toolGroupManager.isToolEligibleForExecution(
                                "delete_file", List.of("danger_tools")))
                .isTrue();
        assertThat(toolGroupManager.isToolEligibleForExecution("delete_file", List.of())).isFalse();
    }

    @Test
    void isToolEligibleForExecution_whenToolIsUngrouped_returnsTrue() {
        toolGroupManager.registerToolGroup(
                ToolGroup.ofToolNames("research", "Research", List.of("search")));

        assertThat(toolGroupManager.isToolEligibleForExecution("clock", List.of())).isTrue();
        assertThat(toolGroupManager.isToolEligibleForExecution("clock", List.of("research")))
                .isTrue();
    }

    @Test
    void isToolEligibleForExecution_unknownOrBlankTool_returnsExpected() {
        toolGroupManager.registerToolGroup(
                ToolGroup.ofToolNames("research", "Research", List.of("search")));

        assertThat(toolGroupManager.isToolEligibleForExecution("missing", List.of())).isTrue();
        assertThat(toolGroupManager.isToolEligibleForExecution("", List.of())).isFalse();
        assertThat(toolGroupManager.isToolEligibleForExecution("   ", List.of())).isFalse();
    }

    @Test
    void unregisterToolGroup_withoutRemainingOwner_restoresUngroupedSemantics() {
        toolGroupManager.registerToolGroup(
                ToolGroup.ofToolNames("research", "Research", List.of("search")));

        toolGroupManager.unregisterToolGroup("research");

        assertThat(toolGroupManager.getActiveToolNames(
                List.of("research"), List.of("search", "clock")))
                .containsExactlyInAnyOrder("clock", "search");
        assertThat(toolGroupManager.isToolEligibleForExecution(
                "search", List.of("research")))
                .isTrue();
    }

    @Test
    void unregisterToolGroup_whenToolHasOtherOwner_usesRemainingGroup() {
        toolGroupManager.registerToolGroup(
                ToolGroup.ofToolNames("research", "Research", List.of("search")));
        toolGroupManager.registerToolGroup(
                ToolGroup.ofToolNames("analysis", "Analysis", List.of("search", "chart")));

        toolGroupManager.unregisterToolGroup("research");

        assertThat(toolGroupManager.getActiveToolNames(
                List.of("analysis"), List.of("search", "chart")))
                .containsExactlyInAnyOrder("chart", "search");
        assertThat(toolGroupManager.isToolEligibleForExecution(
                "search", List.of("analysis")))
                .isTrue();
        assertThat(toolGroupManager.isToolEligibleForExecution(
                "search", List.of("research", "missing")))
                .isFalse();
    }

    @Test
    void reRegisterToolGroup_afterUnregister_restoresGroupAuthorization() {
        ToolGroup researchGroup =
                ToolGroup.ofToolNames("research", "Research", List.of("search"));

        toolGroupManager.registerToolGroup(researchGroup);
        toolGroupManager.unregisterToolGroup("research");
        assertThat(toolGroupManager.isToolEligibleForExecution(
                "search", List.of("research")))
                .isTrue();

        toolGroupManager.registerToolGroup(researchGroup);
        assertThat(toolGroupManager.isToolEligibleForExecution(
                "search", List.of("research")))
                .isTrue();
        assertThat(toolGroupManager.isToolEligibleForExecution("search", List.of()))
                .isFalse();
    }

    @Test
    void getActiveSkillNames_returnsSkillRefsFromActiveGroups() {
        toolGroupManager.registerToolGroup(
                new ToolGroup("research", "Research", List.of(
                        new GroupMemberRef(GroupMemberType.TOOL, "bash"),
                        new GroupMemberRef(GroupMemberType.SKILL, "git-helper"))));
        toolGroupManager.registerToolGroup(
                new ToolGroup("analysis", "Analysis", List.of(
                        new GroupMemberRef(GroupMemberType.SKILL, "data-viz"))));

        var active = toolGroupManager.getActiveSkillNames(List.of("research"));

        assertThat(active).containsExactly("git-helper");
    }

    @Test
    void getActiveSkillNames_whenNoGroupsActive_returnsEmpty() {
        toolGroupManager.registerToolGroup(
                new ToolGroup("research", "Research", List.of(
                        new GroupMemberRef(GroupMemberType.SKILL, "git-helper"))));

        assertThat(toolGroupManager.getActiveSkillNames(List.of())).isEmpty();
    }

    @Test
    void getActiveSkillNames_whenGroupHasNoSkillMembers_returnsEmpty() {
        toolGroupManager.registerToolGroup(
                ToolGroup.ofToolNames("research", "Research", List.of("bash")));

        assertThat(toolGroupManager.getActiveSkillNames(List.of("research"))).isEmpty();
    }

    @Test
    void getUngroupedSkillNames_returnsSkillsNotInAnyGroup() {
        toolGroupManager.registerToolGroup(
                new ToolGroup("research", "Research", List.of(
                        new GroupMemberRef(GroupMemberType.SKILL, "git-helper"))));

        var ungrouped = toolGroupManager.getUngroupedSkillNames(
                List.of("git-helper", "search", "data-viz"));

        assertThat(ungrouped).containsExactly("search", "data-viz");
    }

    @Test
    void getUngroupedSkillNames_whenAllSkillsGrouped_returnsEmpty() {
        toolGroupManager.registerToolGroup(
                new ToolGroup("research", "Research", List.of(
                        new GroupMemberRef(GroupMemberType.SKILL, "git-helper"))));

        assertThat(toolGroupManager.getUngroupedSkillNames(List.of("git-helper"))).isEmpty();
    }

    @Test
    void registerToolGroup_whenGroupHasOnlySkillMembers_doesNotWarn() {
        toolGroupManager.registerToolGroup(
                new ToolGroup("skill_only", "Only skills", List.of(
                        new GroupMemberRef(GroupMemberType.SKILL, "git-helper"))));

        assertThat(toolGroupManager.getGroups()).hasSize(1);
        assertThat(toolGroupManager.getGroups().get(0).skillNames()).containsExactly("git-helper");
    }
}
