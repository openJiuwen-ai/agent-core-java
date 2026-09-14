/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.schema.TeamRole;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for remote bridge briefing text.
 *
 * <p>Mirrors Python's bridge briefing tests for
 * {@code openjiuwen/agent_teams/prompts/bridge_remote_brief.py}.</p>
 */
class BridgeRemoteBriefTest {

    @Test
    void packageExportsMatchPythonAllOrder() {
        assertThat(BridgeRemoteBrief.PYTHON_MODULE)
                .isEqualTo("openjiuwen/agent_teams/prompts/bridge_remote_brief.py");
        assertThat(BridgeRemoteBrief.EXPORTED_SYMBOLS).containsExactly(
                "MemberSummary",
                "build_bridge_persona",
                "build_team_overview"
        );
        assertThat(BridgeRemoteBrief.all()).isSameAs(BridgeRemoteBrief.EXPORTED_SYMBOLS);
        assertThat(BridgeRemoteBrief.exports("MemberSummary")).isTrue();
        assertThat(BridgeRemoteBrief.exports("missing")).isFalse();
    }

    @Test
    void buildBridgePersonaCnContainsIdentityAndContract() {
        String text = BridgeRemoteBrief.buildBridgePersona("codex", "senior python reviewer");

        assertThat(text).contains("codex");
        assertThat(text).contains("senior python reviewer");
        assertThat(text).contains("实际执行者");
        assertThat(text).contains("没有工具");
    }

    @Test
    void buildBridgePersonaEnPassesThroughExecutorContract() {
        String text = BridgeRemoteBrief.buildBridgePersona("claudecode", "pair-programmer", "en");

        assertThat(text).contains("claudecode");
        assertThat(text).contains("VERBATIM");
        assertThat(text).contains("do NOT have tools");
    }

    @Test
    void buildTeamOverviewListsMembersCn() {
        String text = BridgeRemoteBrief.buildTeamOverview(
                "demo",
                List.of(
                        new MemberSummary("leader", TeamRole.LEADER, "planner"),
                        new MemberSummary("alice", TeamRole.TEAMMATE, "dev"),
                        new MemberSummary("codex", TeamRole.BRIDGE_AGENT, "reviewer")
                )
        );

        assertThat(text).contains("团队 demo");
        assertThat(text).contains("leader (leader): planner");
        assertThat(text).contains("alice (teammate): dev");
        assertThat(text).contains("codex (bridge_agent): reviewer");
    }

    @Test
    void buildTeamOverviewEmptyMembersHasNoBulletLines() {
        String text = BridgeRemoteBrief.buildTeamOverview("empty", List.of(), "en");

        assertThat(text).contains("Team empty roster:");
        assertThat(text.lines().filter(line -> line.startsWith("- ")).toList()).isEmpty();
    }

    @Test
    void buildTeamOverviewHandlesNoPersona() {
        String text = BridgeRemoteBrief.buildTeamOverview(
                "demo",
                List.of(new MemberSummary("x", TeamRole.TEAMMATE)),
                "en"
        );

        assertThat(text).contains("x (teammate): (no persona)");
    }
}
