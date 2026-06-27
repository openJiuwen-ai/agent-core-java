/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.core.singleagent.prompts.PromptSection;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code build_team_bridge_section} tests in
 * {@code tests/unit_tests/agent_teams/prompts/test_bridge_section.py}.
 */
class TeamBridgeSectionPythonParityTest {

    @Test
    void emptyRosterReturnsNone() {
        assertThat(TeamPromptSections.buildTeamBridgeSection(TeamRole.LEADER, List.of(), "cn", null)).isEmpty();
        assertThat(TeamPromptSections.buildTeamBridgeSection(TeamRole.LEADER, null, "cn", null)).isEmpty();
    }

    @Test
    void leaderSectionCnListsBridgesAsTeammates() {
        PromptSection section = TeamPromptSections.buildTeamBridgeSection(
                TeamRole.LEADER,
                List.of("codex", "claudecode"),
                "cn",
                null
        ).orElseThrow();

        String body = section.render("cn");

        assertThat(body)
                .contains("`codex`")
                .contains("`claudecode`");
        assertThat(body.contains("teammate") || body.contains("\u5b8c\u5168\u4e00\u81f4")).isTrue();
    }

    @Test
    void teammateSectionCnMentionsRemoteExecutor() {
        PromptSection section = TeamPromptSections.buildTeamBridgeSection(
                TeamRole.TEAMMATE,
                List.of("codex"),
                "cn",
                null
        ).orElseThrow();

        assertThat(section.render("cn"))
                .contains("`codex`")
                .contains("send_message");
    }

    @Test
    void bridgeAgentSelfSectionCnSpecifiesSchedulerContract() {
        PromptSection section = TeamPromptSections.buildTeamBridgeSection(
                TeamRole.BRIDGE_AGENT,
                List.of("codex"),
                "cn",
                "codex"
        ).orElseThrow();

        assertThat(section.render("cn"))
                .contains("`codex`")
                .contains("\u8c03\u5ea6")
                .contains("\u539f\u6837")
                .contains("\u81ea\u52a8\u8f6c\u53d1");
    }

    @Test
    void bridgeAgentSelfSectionEnEmitsVerbatimContract() {
        PromptSection section = TeamPromptSections.buildTeamBridgeSection(
                TeamRole.BRIDGE_AGENT,
                List.of("codex"),
                "en",
                "codex"
        ).orElseThrow();

        assertThat(section.render("en").toLowerCase())
                .contains("scheduler")
                .contains("verbatim")
                .contains("auto-forwarded");
    }

    @Test
    void unknownRoleReturnsNone() {
        assertThat(TeamPromptSections.buildTeamBridgeSection(
                TeamRole.HUMAN_AGENT,
                List.of("codex"),
                "cn",
                null
        )).isEmpty();
    }

    @Test
    void sectionNameAndPriority() {
        PromptSection section = TeamPromptSections.buildTeamBridgeSection(
                TeamRole.LEADER,
                List.of("codex"),
                "cn",
                null
        ).orElseThrow();

        assertThat(section.getName()).isEqualTo(TeamPromptSections.TeamSectionName.BRIDGE);
        assertThat(section.getPriority()).isEqualTo(12);
    }
}
