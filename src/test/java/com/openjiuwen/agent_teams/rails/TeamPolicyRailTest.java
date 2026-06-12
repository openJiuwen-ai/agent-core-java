/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.rails;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.prompts.TeamPromptSections.TeamSectionName;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Tests the team policy prompt-section rail.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/agent_teams/rails/team_policy_rail.py}.</p>
 */
class TeamPolicyRailTest {

    @Test
    void staticSectionsAreBuiltAndInjectedWithoutBackend() {
        FakeAgent agent = new FakeAgent("en");
        TeamPolicyRail rail = new TeamPolicyRail(new TeamPolicyRail.Config(
                TeamRole.LEADER,
                "Be concise.",
                "leader",
                "persistent",
                "plan_mode",
                "en",
                "hybrid",
                "Extra base prompt.",
                null,
                null,
                null,
                false
        ));
        rail.init(agent);

        rail.beforeModelCall().toCompletableFuture().join();

        assertThat(agent.builder.hasSection(TeamSectionName.ROLE)).isTrue();
        assertThat(agent.builder.hasSection(TeamSectionName.WORKFLOW)).isTrue();
        assertThat(agent.builder.hasSection(TeamSectionName.LIFECYCLE)).isTrue();
        assertThat(agent.builder.hasSection(TeamSectionName.PERSONA)).isTrue();
        assertThat(agent.builder.hasSection(TeamSectionName.EXTRA)).isTrue();
        assertThat(agent.builder.hasSection(TeamSectionName.HITT)).isFalse();
        assertThat(agent.builder.hasSection(TeamSectionName.INFO)).isFalse();
        assertThat(agent.builder.hasSection(TeamSectionName.MEMBERS)).isFalse();
    }

    @Test
    void bridgeNamesAreIncludedInStaticSections() {
        FakeBackend backend = new FakeBackend();
        backend.bridgeNames = List.of("bridge-b", "bridge-a");
        TeamPolicyRail rail = new TeamPolicyRail(new TeamPolicyRail.Config(
                TeamRole.LEADER,
                "",
                "leader",
                "temporary",
                "build_mode",
                "en",
                "default",
                null,
                null,
                null,
                backend,
                false
        ));

        assertThat(rail.getStaticSections())
                .extracting(PromptSection::getName)
                .contains(TeamSectionName.BRIDGE);
    }

    @Test
    void dynamicSectionsRefreshAndCacheByMtime() {
        FakeBackend backend = new FakeBackend();
        backend.teamInfo = new TeamPolicyRail.TeamInfoSnapshot("team-a", "Team A", "Goal");
        backend.humanNames = List.of("human-1");
        backend.members = List.of(
                new TeamPolicyRail.TeamMemberSnapshot("leader", "Leader", ""),
                new TeamPolicyRail.TeamMemberSnapshot("dev-1", "Developer", "Builds")
        );
        FakeAgent agent = new FakeAgent("en");
        TeamPolicyRail rail = new TeamPolicyRail(new TeamPolicyRail.Config(
                TeamRole.LEADER,
                "",
                "leader",
                "temporary",
                "build_mode",
                "en",
                "default",
                null,
                "/team",
                "/abs/team",
                backend,
                true
        ));
        rail.init(agent);

        rail.beforeModelCall().toCompletableFuture().join();
        rail.beforeModelCall().toCompletableFuture().join();

        assertThat(agent.builder.hasSection(TeamSectionName.HITT)).isTrue();
        assertThat(agent.builder.hasSection(TeamSectionName.INFO)).isTrue();
        assertThat(agent.builder.hasSection(TeamSectionName.MEMBERS)).isTrue();
        assertThat(agent.builder.getSection(TeamSectionName.INFO).orElseThrow().render("en"))
                .contains("team-a")
                .contains("/team")
                .contains("/abs/team");
        assertThat(agent.builder.getSection(TeamSectionName.MEMBERS).orElseThrow().render("en"))
                .contains("dev-1")
                .doesNotContain("member_name=leader");
        assertThat(backend.teamInfoFetches).hasValue(1);
        assertThat(backend.humanFetches).hasValue(1);
        assertThat(backend.memberFetches).hasValue(1);
        assertThat(backend.teamMtimeProbes).hasValue(2);
        assertThat(backend.memberMtimeProbes).hasValue(2);
    }

    @Test
    void memberSectionsRefreshWhenMemberMtimeChanges() {
        FakeBackend backend = new FakeBackend();
        backend.memberMtime = 1L;
        backend.humanNames = List.of("human-1");
        backend.members = new ArrayList<>(List.of(
                new TeamPolicyRail.TeamMemberSnapshot("leader", "Leader", "")
        ));
        FakeAgent agent = new FakeAgent("en");
        TeamPolicyRail rail = new TeamPolicyRail(new TeamPolicyRail.Config(
                TeamRole.LEADER,
                "",
                "leader",
                "temporary",
                "build_mode",
                "en",
                "default",
                null,
                null,
                null,
                backend,
                false
        ));
        rail.init(agent);

        rail.beforeModelCall().toCompletableFuture().join();
        backend.memberMtime = 2L;
        backend.members = List.of(
                new TeamPolicyRail.TeamMemberSnapshot("leader", "Leader", ""),
                new TeamPolicyRail.TeamMemberSnapshot("qa-1", "QA", "Verifies")
        );
        rail.beforeModelCall().toCompletableFuture().join();

        assertThat(backend.memberFetches).hasValue(2);
        assertThat(agent.builder.getSection(TeamSectionName.MEMBERS).orElseThrow().render("en"))
                .contains("qa-1");
    }

    @Test
    void uninitRemovesStaticAndDynamicSections() {
        FakeBackend backend = new FakeBackend();
        backend.teamInfo = new TeamPolicyRail.TeamInfoSnapshot("team-a", "Team A", "Goal");
        backend.humanNames = List.of("human-1");
        backend.members = List.of(new TeamPolicyRail.TeamMemberSnapshot("dev-1", "Developer", ""));
        FakeAgent agent = new FakeAgent("en");
        TeamPolicyRail rail = new TeamPolicyRail(new TeamPolicyRail.Config(
                TeamRole.LEADER,
                "Persona",
                "leader",
                "temporary",
                "build_mode",
                "en",
                "default",
                "Extra",
                null,
                null,
                backend,
                false
        ));
        rail.init(agent);
        rail.beforeModelCall().toCompletableFuture().join();

        rail.uninit(agent);

        assertThat(agent.builder.getAllSections()).doesNotContainKeys(
                TeamSectionName.ROLE,
                TeamSectionName.WORKFLOW,
                TeamSectionName.LIFECYCLE,
                TeamSectionName.PERSONA,
                TeamSectionName.EXTRA,
                TeamSectionName.HITT,
                TeamSectionName.INFO,
                TeamSectionName.MEMBERS
        );
        assertThat(rail.getSystemPromptBuilder()).isNull();
    }

    private static final class FakeAgent implements TeamPolicyRail.PolicyAgent {
        private final SystemPromptBuilder builder;

        private FakeAgent(String language) {
            this.builder = new SystemPromptBuilder(language);
        }

        @Override
        public SystemPromptBuilder getSystemPromptBuilder() {
            return builder;
        }
    }

    private static final class FakeBackend implements TeamPolicyRail.TeamBackend {
        private Collection<String> bridgeNames = List.of();
        private long teamMtime = 1L;
        private long memberMtime = 1L;
        private TeamPolicyRail.TeamInfoSnapshot teamInfo;
        private List<String> humanNames = List.of();
        private List<TeamPolicyRail.TeamMemberSnapshot> members = List.of();
        private final AtomicInteger teamMtimeProbes = new AtomicInteger();
        private final AtomicInteger memberMtimeProbes = new AtomicInteger();
        private final AtomicInteger teamInfoFetches = new AtomicInteger();
        private final AtomicInteger humanFetches = new AtomicInteger();
        private final AtomicInteger memberFetches = new AtomicInteger();

        @Override
        public Collection<String> bridgeAgentNames() {
            return bridgeNames;
        }

        @Override
        public CompletionStage<Long> getTeamUpdatedAt() {
            teamMtimeProbes.incrementAndGet();
            return CompletableFuture.completedFuture(teamMtime);
        }

        @Override
        public CompletionStage<TeamPolicyRail.TeamInfoSnapshot> getTeamInfo() {
            teamInfoFetches.incrementAndGet();
            return CompletableFuture.completedFuture(teamInfo);
        }

        @Override
        public CompletionStage<Long> getMembersMaxUpdatedAt() {
            memberMtimeProbes.incrementAndGet();
            return CompletableFuture.completedFuture(memberMtime);
        }

        @Override
        public CompletionStage<List<String>> humanAgentNames() {
            humanFetches.incrementAndGet();
            return CompletableFuture.completedFuture(humanNames);
        }

        @Override
        public CompletionStage<List<TeamPolicyRail.TeamMemberSnapshot>> listMembers() {
            memberFetches.incrementAndGet();
            return CompletableFuture.completedFuture(members);
        }
    }
}
