/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.rails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.prompts.TeamPromptSections;
import com.openjiuwen.agent_teams.prompts.TeamPromptSections.TeamSectionName;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests the team policy prompt-section rail.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/agent_teams/rails/team_policy_rail.py}.</p>
 *
 * <p>Mirrors Python's supplemental missing-test coverage in
 * {@code tests/unit_tests/agent_teams/test_team_policy_rail.py}.</p>
 */
class TeamPolicyRailTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_leader_role_section",
            "test_teammate_role_section",
            "test_role_without_member_id",
            "test_leader_workflow",
            "test_leader_workflow_predefined",
            "test_leader_workflow_hybrid",
            "test_teammate_returns_none_workflow",
            "test_leader_temporary",
            "test_leader_persistent",
            "test_teammate_returns_none_lifecycle",
            "test_with_persona",
            "test_empty_persona_returns_none",
            "test_with_base_prompt",
            "test_empty_extra_returns_none",
            "test_full_info",
            "test_empty_info_returns_none",
            "test_team_workspace_mount_appended",
            "test_team_workspace_only",
            "test_excludes_self",
            "test_no_peers_returns_none",
            "test_empty_members_returns_none",
            "test_leader_rail_registers_static_sections_without_backend",
            "test_teammate_rail_omits_leader_only_sections",
            "test_first_call_loads_from_db",
            "test_cache_hit_skips_full_query",
            "test_cache_miss_when_member_added",
            "test_status_update_does_not_refetch",
            "test_team_workspace_mount_preserved_after_refresh",
            "test_priority_order_in_built_prompt",
            "test_uninit_removes_static_and_dynamic_sections"
    );

    @TestFactory
    Collection<DynamicTest> pythonTeamPolicyRailCases() {
        return PYTHON_TESTS.stream()
                .map(name -> dynamicTest(name, () -> runPythonTeamPolicyRailCase(name)))
                .toList();
    }

    private void runPythonTeamPolicyRailCase(String name) {
        switch (name) {
            case "test_leader_role_section" -> assertLeaderRoleSection();
            case "test_teammate_role_section" -> assertTeammateRoleSection();
            case "test_role_without_member_id" -> assertRoleWithoutMemberId();
            case "test_leader_workflow" -> assertLeaderWorkflow("default", "spawn_member");
            case "test_leader_workflow_predefined" -> assertLeaderWorkflow("predefined", "\u9884\u5b9a\u4e49\u56e2\u961f\u6a21\u5f0f");
            case "test_leader_workflow_hybrid" -> assertLeaderWorkflow("hybrid", "\u6df7\u5408\u56e2\u961f\u6a21\u5f0f");
            case "test_teammate_returns_none_workflow" -> assertTeammateWorkflowReturnsNone();
            case "test_leader_temporary" -> assertLeaderLifecycle("temporary", "shutdown_member");
            case "test_leader_persistent" -> assertLeaderLifecycle("persistent", "# \u56e2\u961f\u751f\u547d\u5468\u671f");
            case "test_teammate_returns_none_lifecycle" -> assertTeammateLifecycleReturnsNone();
            case "test_with_persona" -> assertPersonaSection();
            case "test_empty_persona_returns_none" -> assertEmptyPersonaReturnsNone();
            case "test_with_base_prompt" -> assertExtraSection();
            case "test_empty_extra_returns_none" -> assertEmptyExtraReturnsNone();
            case "test_full_info" -> assertFullInfoSection();
            case "test_empty_info_returns_none" -> assertEmptyInfoReturnsNone();
            case "test_team_workspace_mount_appended" -> assertWorkspaceMountAppended();
            case "test_team_workspace_only" -> assertWorkspaceOnlySection();
            case "test_excludes_self" -> assertMembersSectionExcludesSelf();
            case "test_no_peers_returns_none" -> assertNoPeersReturnsNone();
            case "test_empty_members_returns_none" -> assertEmptyMembersReturnsNone();
            case "test_leader_rail_registers_static_sections_without_backend" ->
                    assertLeaderRailRegistersStaticSectionsWithoutBackend();
            case "test_teammate_rail_omits_leader_only_sections" -> assertTeammateRailOmitsLeaderOnlySections();
            case "test_first_call_loads_from_db" -> assertFirstCallLoadsFromDb();
            case "test_cache_hit_skips_full_query" -> assertCacheHitSkipsFullQuery();
            case "test_cache_miss_when_member_added" -> assertCacheMissWhenMemberAdded();
            case "test_status_update_does_not_refetch" -> assertStatusUpdateDoesNotRefetch();
            case "test_team_workspace_mount_preserved_after_refresh" -> assertWorkspaceMountPreservedAfterRefresh();
            case "test_priority_order_in_built_prompt" -> assertPriorityOrderInBuiltPrompt();
            case "test_uninit_removes_static_and_dynamic_sections" -> assertUninitRemovesStaticAndDynamicSections();
            default -> throw new IllegalArgumentException("Unhandled Python test: " + name);
        }
    }

    private static void assertLeaderRoleSection() {
        PromptSection section = TeamPromptSections.buildTeamRoleSection(
                TeamRole.LEADER,
                "leader1",
                "build_mode",
                "cn"
        );

        assertThat(section.getName()).isEqualTo(TeamSectionName.ROLE);
        assertThat(section.getPriority()).isEqualTo(11);
        assertThat(section.render("cn"))
                .contains("# \u56e2\u961f\u89d2\u8272")
                .contains("\u4f60\u7684 member_name: leader1")
                .contains("create_task");
    }

    private static void assertTeammateRoleSection() {
        PromptSection section = TeamPromptSections.buildTeamRoleSection(
                TeamRole.TEAMMATE,
                "dev1",
                "build_mode",
                "cn"
        );

        assertThat(section.render("cn")).contains("view_task");
    }

    private static void assertRoleWithoutMemberId() {
        PromptSection section = TeamPromptSections.buildTeamRoleSection(
                TeamRole.LEADER,
                null,
                "build_mode",
                "cn"
        );

        assertThat(section.render("cn")).doesNotContain("\u4f60\u7684 member_name");
    }

    private static void assertLeaderWorkflow(String mode, String expectedContent) {
        PromptSection section = TeamPromptSections.buildTeamWorkflowSection(TeamRole.LEADER, mode, "cn")
                .orElseThrow();

        assertThat(section.getName()).isEqualTo(TeamSectionName.WORKFLOW);
        assertThat(section.getPriority()).isEqualTo(13);
        assertThat(section.render("cn"))
                .contains("# \u5de5\u4f5c\u6d41\u7a0b")
                .contains(expectedContent);
    }

    private static void assertTeammateWorkflowReturnsNone() {
        assertThat(TeamPromptSections.buildTeamWorkflowSection(TeamRole.TEAMMATE, "default", "cn")).isEmpty();
    }

    private static void assertLeaderLifecycle(String lifecycle, String expectedContent) {
        PromptSection section = TeamPromptSections.buildTeamLifecycleSection(TeamRole.LEADER, lifecycle, "cn")
                .orElseThrow();

        assertThat(section.getName()).isEqualTo(TeamSectionName.LIFECYCLE);
        assertThat(section.getPriority()).isEqualTo(14);
        assertThat(section.render("cn"))
                .contains("# \u56e2\u961f\u751f\u547d\u5468\u671f")
                .contains(expectedContent);
    }

    private static void assertTeammateLifecycleReturnsNone() {
        assertThat(TeamPromptSections.buildTeamLifecycleSection(TeamRole.TEAMMATE, "temporary", "cn")).isEmpty();
    }

    private static void assertPersonaSection() {
        PromptSection section = TeamPromptSections.buildTeamPersonaSection("PM Expert", "cn").orElseThrow();

        assertThat(section.getName()).isEqualTo(TeamSectionName.PERSONA);
        assertThat(section.getPriority()).isEqualTo(15);
        assertThat(section.render("cn"))
                .contains("# \u5f53\u524d\u4eba\u8bbe")
                .contains("PM Expert");
    }

    private static void assertEmptyPersonaReturnsNone() {
        assertThat(TeamPromptSections.buildTeamPersonaSection("", "cn")).isEmpty();
        assertThat(TeamPromptSections.buildTeamPersonaSection(null, "cn")).isEmpty();
    }

    private static void assertExtraSection() {
        PromptSection section = TeamPromptSections.buildTeamExtraSection("Be careful", "cn").orElseThrow();

        assertThat(section.getName()).isEqualTo(TeamSectionName.EXTRA);
        assertThat(section.getPriority()).isEqualTo(16);
        assertThat(section.render("cn")).contains("Be careful");
    }

    private static void assertEmptyExtraReturnsNone() {
        assertThat(TeamPromptSections.buildTeamExtraSection(null, "cn")).isEmpty();
        assertThat(TeamPromptSections.buildTeamExtraSection("   ", "cn")).isEmpty();
    }

    private static void assertFullInfoSection() {
        PromptSection section = TeamPromptSections.buildTeamInfoSection(
                Map.of("team_name", "AlphaTeam", "desc", "Build a thing"),
                null,
                null,
                "cn"
        ).orElseThrow();

        assertThat(section.getName()).isEqualTo(TeamSectionName.INFO);
        assertThat(section.getPriority()).isEqualTo(65);
        assertThat(section.render("cn"))
                .contains("# \u56e2\u961f\u4fe1\u606f")
                .contains("AlphaTeam")
                .contains("Build a thing");
    }

    private static void assertEmptyInfoReturnsNone() {
        assertThat(TeamPromptSections.buildTeamInfoSection(null, null, null, "cn")).isEmpty();
        assertThat(TeamPromptSections.buildTeamInfoSection(Map.of(), null, null, "cn")).isEmpty();
        assertThat(TeamPromptSections.buildTeamInfoSection(Map.of("unrelated", "value"), null, null, "cn")).isEmpty();
    }

    private static void assertWorkspaceMountAppended() {
        PromptSection section = TeamPromptSections.buildTeamInfoSection(
                Map.of("team_name", "AlphaTeam", "desc", "Build a thing"),
                ".team/alpha/",
                "/abs/team-workspace",
                "cn"
        ).orElseThrow();

        assertThat(section.render("cn"))
                .contains("\u56e2\u961f\u5171\u4eab\u5de5\u4f5c\u7a7a\u95f4")
                .contains("`.team/alpha/`")
                .contains("`/abs/team-workspace`");
    }

    private static void assertWorkspaceOnlySection() {
        PromptSection section = TeamPromptSections.buildTeamInfoSection(null, ".team/solo/", null, "en")
                .orElseThrow();

        assertThat(section.render("en"))
                .contains("Team Shared Workspace")
                .contains("`.team/solo/`");
    }

    private static void assertMembersSectionExcludesSelf() {
        PromptSection section = TeamPromptSections.buildTeamMembersSection(
                List.of(
                        Map.of("member_name", "leader1", "display_name", "Leader", "desc", "PM"),
                        Map.of("member_name", "dev1", "display_name", "Dev", "desc", "Coder")
                ),
                "leader1",
                "cn"
        ).orElseThrow();

        assertThat(section.getName()).isEqualTo(TeamSectionName.MEMBERS);
        assertThat(section.getPriority()).isEqualTo(66);
        assertThat(section.render("cn"))
                .contains("# \u6210\u5458\u5173\u7cfb")
                .contains("Dev")
                .doesNotContain("Leader");
    }

    private static void assertNoPeersReturnsNone() {
        assertThat(TeamPromptSections.buildTeamMembersSection(
                List.of(Map.of("member_name", "self", "display_name", "Me")),
                "self",
                "cn"
        )).isEmpty();
    }

    private static void assertEmptyMembersReturnsNone() {
        assertThat(TeamPromptSections.buildTeamMembersSection(null, "x", "cn")).isEmpty();
    }

    private static void assertLeaderRailRegistersStaticSectionsWithoutBackend() {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        FakeAgent agent = new FakeAgent(builder);
        TeamPolicyRail rail = new TeamPolicyRail(new TeamPolicyRail.Config(
                TeamRole.LEADER,
                "PM Expert",
                "leader1",
                "temporary",
                "build_mode",
                "cn",
                "default",
                "Stay sharp",
                null,
                null,
                null,
                false
        ));

        rail.init(agent);
        rail.beforeModelCall().toCompletableFuture().join();

        assertThat(builder.hasSection(TeamSectionName.ROLE)).isTrue();
        assertThat(builder.hasSection(TeamSectionName.WORKFLOW)).isTrue();
        assertThat(builder.hasSection(TeamSectionName.LIFECYCLE)).isTrue();
        assertThat(builder.hasSection(TeamSectionName.PERSONA)).isTrue();
        assertThat(builder.hasSection(TeamSectionName.EXTRA)).isTrue();
        assertThat(builder.hasSection(TeamSectionName.INFO)).isFalse();
        assertThat(builder.hasSection(TeamSectionName.MEMBERS)).isFalse();
    }

    private static void assertTeammateRailOmitsLeaderOnlySections() {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        FakeAgent agent = new FakeAgent(builder);
        TeamPolicyRail rail = new TeamPolicyRail(new TeamPolicyRail.Config(
                TeamRole.TEAMMATE,
                "Coder",
                "dev1",
                "temporary",
                "build_mode",
                "cn",
                "default",
                null,
                null,
                null,
                null,
                false
        ));

        rail.init(agent);
        rail.beforeModelCall().toCompletableFuture().join();

        assertThat(builder.hasSection(TeamSectionName.WORKFLOW)).isFalse();
        assertThat(builder.hasSection(TeamSectionName.LIFECYCLE)).isFalse();
        assertThat(builder.hasSection(TeamSectionName.EXTRA)).isFalse();
        assertThat(builder.hasSection(TeamSectionName.ROLE)).isTrue();
        assertThat(builder.hasSection(TeamSectionName.PERSONA)).isTrue();
    }

    private static void assertFirstCallLoadsFromDb() {
        FakeBackend backend = new FakeBackend();
        backend.teamInfo = new TeamPolicyRail.TeamInfoSnapshot("Beta", "Test team", "");
        backend.members = List.of(
                new TeamPolicyRail.TeamMemberSnapshot("leader1", "Leader", "PM"),
                new TeamPolicyRail.TeamMemberSnapshot("dev1", "Dev", "Coder")
        );
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        TeamPolicyRail rail = leaderRail(builder, backend, null, null);

        rail.beforeModelCall().toCompletableFuture().join();

        assertThat(builder.hasSection(TeamSectionName.INFO)).isTrue();
        assertThat(builder.hasSection(TeamSectionName.MEMBERS)).isTrue();
        assertThat(backend.teamInfoFetches).hasValue(1);
        assertThat(backend.memberFetches).hasValue(1);
        assertThat(builder.getSection(TeamSectionName.MEMBERS).orElseThrow().render("cn"))
                .contains("Dev")
                .doesNotContain("Leader");
    }

    private static void assertCacheHitSkipsFullQuery() {
        FakeBackend backend = new FakeBackend();
        backend.teamInfo = new TeamPolicyRail.TeamInfoSnapshot("Beta", "Test", "");
        backend.members = List.of(new TeamPolicyRail.TeamMemberSnapshot("dev1", "Dev", ""));
        TeamPolicyRail rail = leaderRail(new SystemPromptBuilder("cn"), backend, null, null);

        rail.beforeModelCall().toCompletableFuture().join();
        rail.beforeModelCall().toCompletableFuture().join();
        rail.beforeModelCall().toCompletableFuture().join();

        assertThat(backend.teamMtimeProbes).hasValue(3);
        assertThat(backend.memberMtimeProbes).hasValue(3);
        assertThat(backend.teamInfoFetches).hasValue(1);
        assertThat(backend.memberFetches).hasValue(1);
    }

    private static void assertCacheMissWhenMemberAdded() {
        FakeBackend backend = new FakeBackend();
        backend.teamInfo = new TeamPolicyRail.TeamInfoSnapshot("Beta", "Test", "");
        backend.memberMtime = 1L;
        backend.members = new ArrayList<>(List.of(new TeamPolicyRail.TeamMemberSnapshot("dev1", "Dev", "")));
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        TeamPolicyRail rail = leaderRail(builder, backend, null, null);

        rail.beforeModelCall().toCompletableFuture().join();
        assertThat(builder.getSection(TeamSectionName.MEMBERS).orElseThrow().render("cn"))
                .contains("Dev")
                .doesNotContain("Newbie");

        backend.memberMtime = 2L;
        backend.members = List.of(
                new TeamPolicyRail.TeamMemberSnapshot("dev1", "Dev", ""),
                new TeamPolicyRail.TeamMemberSnapshot("dev2", "Newbie", "fresh")
        );
        rail.beforeModelCall().toCompletableFuture().join();

        assertThat(builder.getSection(TeamSectionName.MEMBERS).orElseThrow().render("cn")).contains("Newbie");
        assertThat(backend.memberFetches).hasValue(2);
    }

    private static void assertStatusUpdateDoesNotRefetch() {
        FakeBackend backend = new FakeBackend();
        backend.teamInfo = new TeamPolicyRail.TeamInfoSnapshot("Beta", "Test", "");
        backend.memberMtime = 42L;
        backend.members = List.of(new TeamPolicyRail.TeamMemberSnapshot("dev1", "Dev", ""));
        TeamPolicyRail rail = leaderRail(new SystemPromptBuilder("cn"), backend, null, null);

        rail.beforeModelCall().toCompletableFuture().join();
        rail.beforeModelCall().toCompletableFuture().join();

        assertThat(backend.memberFetches).hasValue(1);
    }

    private static void assertWorkspaceMountPreservedAfterRefresh() {
        FakeBackend backend = new FakeBackend();
        backend.teamInfo = new TeamPolicyRail.TeamInfoSnapshot("Beta", "Test", "");
        backend.members = List.of(new TeamPolicyRail.TeamMemberSnapshot("dev1", "Dev", ""));
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        TeamPolicyRail rail = leaderRail(builder, backend, ".team/beta/", "/abs/team-workspace");

        rail.beforeModelCall().toCompletableFuture().join();
        assertThat(builder.getSection(TeamSectionName.INFO).orElseThrow().render("cn"))
                .contains("`.team/beta/`");

        backend.teamMtime = 99L;
        backend.teamInfo = new TeamPolicyRail.TeamInfoSnapshot("Beta-renamed", "Test", "");
        rail.beforeModelCall().toCompletableFuture().join();

        assertThat(builder.getSection(TeamSectionName.INFO).orElseThrow().render("cn"))
                .contains("`.team/beta/`")
                .contains("Beta-renamed");
    }

    private static void assertPriorityOrderInBuiltPrompt() {
        FakeBackend backend = new FakeBackend();
        backend.teamInfo = new TeamPolicyRail.TeamInfoSnapshot("T1", "D", "");
        backend.members = List.of(new TeamPolicyRail.TeamMemberSnapshot("dev1", "D", ""));
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        TeamPolicyRail rail = leaderRail(builder, backend, null, null);

        rail.beforeModelCall().toCompletableFuture().join();

        String prompt = builder.build();
        int idxRole = prompt.indexOf("# \u56e2\u961f\u89d2\u8272");
        int idxWorkflow = prompt.indexOf("# \u5de5\u4f5c\u6d41\u7a0b");
        int idxLifecycle = prompt.indexOf("# \u56e2\u961f\u751f\u547d\u5468\u671f");
        int idxPersona = prompt.indexOf("# \u5f53\u524d\u4eba\u8bbe");
        int idxInfo = prompt.indexOf("# \u56e2\u961f\u4fe1\u606f");
        int idxMembers = prompt.indexOf("# \u6210\u5458\u5173\u7cfb");
        assertThat(idxRole).isLessThan(idxWorkflow);
        assertThat(idxWorkflow).isLessThan(idxLifecycle);
        assertThat(idxLifecycle).isLessThan(idxPersona);
        assertThat(idxPersona).isLessThan(idxInfo);
        assertThat(idxInfo).isLessThan(idxMembers);
    }

    private static void assertUninitRemovesStaticAndDynamicSections() {
        FakeBackend backend = new FakeBackend();
        backend.teamInfo = new TeamPolicyRail.TeamInfoSnapshot("T", "D", "");
        backend.members = List.of(new TeamPolicyRail.TeamMemberSnapshot("dev1", "Dev", ""));
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        FakeAgent agent = new FakeAgent(builder);
        TeamPolicyRail rail = leaderRail(agent, backend, null, null);

        rail.beforeModelCall().toCompletableFuture().join();
        rail.uninit(agent);

        assertThat(builder.getAllSections()).doesNotContainKeys(
                TeamSectionName.ROLE,
                TeamSectionName.WORKFLOW,
                TeamSectionName.LIFECYCLE,
                TeamSectionName.PERSONA,
                TeamSectionName.INFO,
                TeamSectionName.MEMBERS
        );
        assertThat(rail.getSystemPromptBuilder()).isNull();
    }

    private static TeamPolicyRail leaderRail(
            SystemPromptBuilder builder,
            FakeBackend backend,
            String workspaceMount,
            String workspacePath
    ) {
        FakeAgent agent = new FakeAgent(builder);
        return leaderRail(agent, backend, workspaceMount, workspacePath);
    }

    private static TeamPolicyRail leaderRail(
            FakeAgent agent,
            FakeBackend backend,
            String workspaceMount,
            String workspacePath
    ) {
        TeamPolicyRail rail = new TeamPolicyRail(new TeamPolicyRail.Config(
                TeamRole.LEADER,
                "PM",
                "leader1",
                "temporary",
                "build_mode",
                "cn",
                "default",
                null,
                workspaceMount,
                workspacePath,
                backend,
                false
        ));
        rail.init(agent);
        return rail;
    }

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

        private FakeAgent(SystemPromptBuilder builder) {
            this.builder = builder;
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
