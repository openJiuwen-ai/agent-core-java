/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Supplemental parity coverage for unified team-member system prompt builders.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.prompts.test_member_system_prompt} in
 * {@code tests/unit_tests/agent_teams/prompts/test_member_system_prompt.py}.</p>
 */
class TeamMemberSystemPromptMissingTest {

    @Test
    void staticSectionsTeammateHasRoleAndPersona() {
        List<PromptSection> sections = TeamPromptSections.buildTeamStaticSections(
                TeamRole.TEAMMATE,
                "backend expert",
                "dev-1",
                "temporary",
                "build_mode",
                "default",
                null,
                "en",
                null,
                false,
                null);

        Set<String> names = sectionNames(sections);
        assertThat(names).contains(TeamPromptSections.TeamSectionName.ROLE);
        assertThat(names).contains(TeamPromptSections.TeamSectionName.PERSONA);
        assertThat(names).doesNotContain(TeamPromptSections.TeamSectionName.WORKFLOW);
        assertThat(names).doesNotContain(TeamPromptSections.TeamSectionName.LIFECYCLE);
    }

    @Test
    void staticSectionsLeaderIncludesWorkflowAndLifecycle() {
        List<PromptSection> sections = TeamPromptSections.buildTeamStaticSections(
                TeamRole.LEADER,
                "",
                "leader",
                "temporary",
                "build_mode",
                "default",
                null,
                "en",
                null,
                false,
                null);

        Set<String> names = sectionNames(sections);
        assertThat(names).contains(TeamPromptSections.TeamSectionName.ROLE);
        assertThat(names).contains(TeamPromptSections.TeamSectionName.WORKFLOW);
        assertThat(names).contains(TeamPromptSections.TeamSectionName.LIFECYCLE);
        assertThat(names).doesNotContain(TeamPromptSections.TeamSectionName.PERSONA);
    }

    @Test
    void staticSectionsExcludeDynamicInfoAndMembers() {
        List<PromptSection> sections = TeamPromptSections.buildTeamStaticSections(
                TeamRole.LEADER,
                "x",
                "leader",
                "temporary",
                "build_mode",
                "default",
                null,
                "en",
                null,
                false,
                null);

        Set<String> names = sectionNames(sections);
        assertThat(names).doesNotContain(TeamPromptSections.TeamSectionName.INFO);
        assertThat(names).doesNotContain(TeamPromptSections.TeamSectionName.MEMBERS);
    }

    @Test
    void memberSystemPromptRendersPersonaAndMemberName() {
        String prompt = TeamPromptSections.buildTeamMemberSystemPrompt(
                TeamRole.TEAMMATE,
                "backend expert",
                "dev-1",
                "temporary",
                "build_mode",
                "default",
                null,
                "en",
                null,
                false,
                null);

        assertThat(prompt.strip()).isNotEmpty();
        assertThat(prompt).contains("backend expert");
        assertThat(prompt).contains("dev-1");
    }

    @Test
    void memberSystemPromptNonemptyWithoutPersona() {
        String prompt = TeamPromptSections.buildTeamMemberSystemPrompt(
                TeamRole.TEAMMATE,
                "",
                "dev-1",
                "temporary",
                "build_mode",
                "default",
                null,
                "en",
                null,
                false,
                null);

        assertThat(prompt.strip()).isNotEmpty();
    }

    private static Set<String> sectionNames(List<PromptSection> sections) {
        return sections.stream()
                .map(PromptSection::getName)
                .collect(Collectors.toSet());
    }
}
