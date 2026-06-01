/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.agent_teams.agent.TeamRail;
import com.openjiuwen.agent_teams.agent.TeamSectionName;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;

/**
 * Tests for TeamRail and its section builders.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.agent_teams.test_team_rail}.
 */
class TestTeamRail {

    // ---------------------------------------------------------------------------
    // Section builders
    // ---------------------------------------------------------------------------

    @Nested
    class TestTeamRoleSection {

        @Test
        @Tag("level0")
        void testLeaderRoleSection() {
            PromptSection section = TeamRail.buildTeamRoleSection(
                    TeamRole.LEADER,
                    "leader1",
                    "build_mode",
                    "cn"
            );
            assertNotNull(section);
            assertEquals(TeamSectionName.ROLE, section.getName());
            assertEquals(11, section.getPriority());

            String content = section.render("cn");
            assertTrue(content.contains("# 团队角色"));
            // Check full member_name line as in Python
            assertTrue(content.contains("你的 member_name: leader1"));
            assertTrue(content.contains("build_mode"));
            // Check leader policy content
            assertTrue(content.contains("create_task"));
        }

        @Test
        @Tag("level0")
        void testTeammateRoleSection() {
            PromptSection section = TeamRail.buildTeamRoleSection(
                    TeamRole.TEAMMATE,
                    "dev1",
                    "build_mode",
                    "cn"
            );
            assertNotNull(section);
            String content = section.render("cn");
            assertTrue(content.contains("# 团队角色"));
            assertTrue(content.contains("build_mode"));
            // Check teammate policy content
            assertTrue(content.contains("view_task"));
        }

        @Test
        @Tag("level0")
        void testRoleWithoutMemberId() {
            PromptSection section = TeamRail.buildTeamRoleSection(
                    TeamRole.LEADER,
                    null,
                    "build_mode",
                    "cn"
            );
            assertNotNull(section);
            String content = section.render("cn");
            // Check that member_name line is NOT present
            assertFalse(content.contains("你的 member_name"));
        }
    }

    @Nested
    class TestTeamWorkflowSection {

        @Test
        @Tag("level0")
        void testLeaderWorkflow() {
            PromptSection section = TeamRail.buildTeamWorkflowSection(
                    TeamRole.LEADER,
                    "default",
                    "cn"
            );
            assertNotNull(section);
            assertEquals(13, section.getPriority());
            String content = section.render("cn");
            assertTrue(content.contains("# 工作流程"));
            assertTrue(content.contains("spawn_member"));
        }

        @Test
        @Tag("level0")
        void testLeaderWorkflowPredefined() {
            PromptSection section = TeamRail.buildTeamWorkflowSection(
                    TeamRole.LEADER,
                    "predefined",
                    "cn"
            );
            assertNotNull(section);
            String content = section.render("cn");
            assertTrue(content.contains("预定义团队模式"));
        }

        @Test
        @Tag("level0")
        void testLeaderWorkflowHybrid() {
            PromptSection section = TeamRail.buildTeamWorkflowSection(
                    TeamRole.LEADER,
                    "hybrid",
                    "cn"
            );
            assertNotNull(section);
            String content = section.render("cn");
            assertTrue(content.contains("混合团队模式"));
            assertTrue(content.contains("spawn_member"));
        }

        @Test
        @Tag("level0")
        void testTeammateReturnsNull() {
            PromptSection section = TeamRail.buildTeamWorkflowSection(
                    TeamRole.TEAMMATE,
                    "default",
                    "cn"
            );
            assertNull(section);
        }
    }

    @Nested
    class TestTeamLifecycleSection {

        @Test
        @Tag("level0")
        void testLeaderTemporary() {
            PromptSection section = TeamRail.buildTeamLifecycleSection(
                    TeamRole.LEADER,
                    "temporary",
                    "cn"
            );
            assertNotNull(section);
            assertEquals(14, section.getPriority());
            String content = section.render("cn");
            assertTrue(content.contains("# 团队生命周期"));
            assertTrue(content.contains("shutdown_member"));
        }

        @Test
        @Tag("level0")
        void testLeaderPersistent() {
            PromptSection section = TeamRail.buildTeamLifecycleSection(
                    TeamRole.LEADER,
                    "persistent",
                    "cn"
            );
            assertNotNull(section);
            String content = section.render("cn");
            // persistent template has different content
            assertTrue(content.contains("# 团队生命周期"));
        }

        @Test
        @Tag("level0")
        void testTeammateReturnsNull() {
            PromptSection section = TeamRail.buildTeamLifecycleSection(
                    TeamRole.TEAMMATE,
                    "temporary",
                    "cn"
            );
            assertNull(section);
        }
    }

    @Nested
    class TestTeamPersonaSection {

        @Test
        @Tag("level0")
        void testWithPersona() {
            PromptSection section = TeamRail.buildTeamPersonaSection(
                    "PM Expert",
                    "cn"
            );
            assertNotNull(section);
            assertEquals(15, section.getPriority());
            String content = section.render("cn");
            assertTrue(content.contains("# 当前人设"));
            assertTrue(content.contains("PM Expert"));
        }

        @Test
        @Tag("level1")
        void testEmptyPersonaReturnsNull() {
            assertNull(TeamRail.buildTeamPersonaSection("", "cn"));
            assertNull(TeamRail.buildTeamPersonaSection(null, "cn"));
        }
    }

    @Nested
    class TestTeamExtraSection {

        @Test
        @Tag("level1")
        void testWithBasePrompt() {
            PromptSection section = TeamRail.buildTeamExtraSection(
                    "Be careful",
                    "cn"
            );
            assertNotNull(section);
            assertEquals(16, section.getPriority());
            String content = section.render("cn");
            assertTrue(content.contains("Be careful"));
        }

        @Test
        @Tag("level1")
        void testEmptyReturnsNull() {
            assertNull(TeamRail.buildTeamExtraSection(null, "cn"));
            assertNull(TeamRail.buildTeamExtraSection("   ", "cn"));
        }
    }

    @Nested
    class TestTeamInfoSection {

        @Test
        @Tag("level1")
        void testFullInfo() {
            Map<String, Object> teamInfo = new HashMap<>();
            teamInfo.put("team_name", "AlphaTeam");
            teamInfo.put("desc", "Build a thing");

            PromptSection section = TeamRail.buildTeamInfoSection(
                    teamInfo,
                    null,
                    null,
                    "cn"
            );
            assertNotNull(section);
            assertEquals(65, section.getPriority());
            String content = section.render("cn");
            assertTrue(content.contains("# 团队信息"));
            assertTrue(content.contains("AlphaTeam"));
            assertTrue(content.contains("Build a thing"));
        }

        @Test
        @Tag("level1")
        void testEmptyReturnsNull() {
            assertNull(TeamRail.buildTeamInfoSection(null, null, null, "cn"));
            assertNull(TeamRail.buildTeamInfoSection(new HashMap<>(), null, null, "cn"));
            
            Map<String, Object> unrelated = new HashMap<>();
            unrelated.put("unrelated", "value");
            assertNull(TeamRail.buildTeamInfoSection(unrelated, null, null, "cn"));
        }

        @Test
        @Tag("level1")
        void testTeamWorkspaceMountAppended() {
            Map<String, Object> teamInfo = new HashMap<>();
            teamInfo.put("team_name", "AlphaTeam");
            teamInfo.put("desc", "Build a thing");

            PromptSection section = TeamRail.buildTeamInfoSection(
                    teamInfo,
                    ".team/alpha/",
                    "/abs/team-workspace",
                    "cn"
            );
            assertNotNull(section);
            String content = section.render("cn");
            assertTrue(content.contains("团队共享工作空间"));
            assertTrue(content.contains(".team/alpha/"));
            assertTrue(content.contains("系统自动管理版本"));
            assertTrue(content.contains("/abs/team-workspace"));
        }

        @Test
        @Tag("level1")
        void testTeamWorkspaceOnly() {
            // Workspace info alone (no name/desc) is still enough to emit the section
            PromptSection section = TeamRail.buildTeamInfoSection(
                    null,
                    ".team/solo/",
                    null,
                    "en"
            );
            assertNotNull(section);
            String content = section.render("en");
            assertTrue(content.contains("Team Shared Workspace"));
            assertTrue(content.contains(".team/solo/"));
            assertTrue(content.contains("Versioning and file locks"));
        }
    }

    @Nested
    class TestTeamHittSection {

        @Test
        @Tag("level0")
        void testNoneWhenNoHumanMembers() {
            assertNull(TeamRail.buildTeamHittSection(
                    TeamRole.LEADER,
                    Collections.emptyList(),
                    "cn",
                    null
            ));
        }

        @Test
        @Tag("level0")
        void testLeaderMentionsLockRules() {
            PromptSection section = TeamRail.buildTeamHittSection(
                    TeamRole.LEADER,
                    List.of("human_agent"),
                    "cn",
                    null
            );
            assertNotNull(section);
            String body = section.render("cn");
            assertTrue(body.contains("human_agent"));
            assertTrue(body.contains("send_message"));
            assertTrue(body.contains("不能") || body.contains("禁止"));
        }

        @Test
        @Tag("level0")
        void testHumanAgentDescribesConstrainedTools() {
            PromptSection section = TeamRail.buildTeamHittSection(
                    TeamRole.HUMAN_AGENT,
                    List.of("human_agent"),
                    "en",
                    "human_agent"
            );
            assertNotNull(section);
            String body = section.render("en");
            assertTrue(body.contains("send_message"));
            assertTrue(body.contains("claim_task") || body.toLowerCase(Locale.ROOT).contains("do not"));
        }

        @Test
        @Tag("level0")
        void testLeaderListsEveryHumanMember() {
            PromptSection section = TeamRail.buildTeamHittSection(
                    TeamRole.LEADER,
                    List.of("human_designer", "human_pm"),
                    "cn",
                    null
            );
            assertNotNull(section);
            String body = section.render("cn");
            assertTrue(body.contains("human_designer"));
            assertTrue(body.contains("human_pm"));
        }
    }

    @Nested
    class TestTeamMembersSection {

        @Test
        @Tag("level1")
        void testExcludesSelf() {
            List<Map<String, String>> members = new ArrayList<>();
            Map<String, String> leader = new HashMap<>();
            leader.put("member_name", "leader1");
            leader.put("display_name", "Leader");
            leader.put("desc", "PM");
            members.add(leader);

            Map<String, String> dev = new HashMap<>();
            dev.put("member_name", "dev1");
            dev.put("display_name", "Dev");
            dev.put("desc", "Coder");
            members.add(dev);

            PromptSection section = TeamRail.buildTeamMembersSection(
                    members,
                    "leader1",
                    "cn"
            );
            assertNotNull(section);
            assertEquals(66, section.getPriority());
            String content = section.render("cn");
            assertTrue(content.contains("# 成员关系"));
            assertTrue(content.contains("Dev"));
            assertFalse(content.contains("Leader"));
        }

        @Test
        @Tag("level1")
        void testNoPeersReturnsNull() {
            List<Map<String, String>> members = new ArrayList<>();
            Map<String, String> self = new HashMap<>();
            self.put("member_name", "self");
            self.put("display_name", "Me");
            members.add(self);

            PromptSection section = TeamRail.buildTeamMembersSection(
                    members,
                    "self",
                    "cn"
            );
            assertNull(section);
        }

        @Test
        @Tag("level1")
        void testEmptyReturnsNull() {
            assertNull(TeamRail.buildTeamMembersSection(null, "x", "cn"));
        }
    }

    // ---------------------------------------------------------------------------
    // TeamRail
    // ---------------------------------------------------------------------------

    /** Minimal stand-in exposing only the system_prompt_builder attribute. */
    private static class StubAgent {
        private final SystemPromptBuilder systemPromptBuilder;

        StubAgent(SystemPromptBuilder builder) {
            this.systemPromptBuilder = builder;
        }

        public SystemPromptBuilder getSystemPromptBuilder() {
            return systemPromptBuilder;
        }
    }

    /** Lightweight stand-in for the SQLModel TeamMember row. */
    private static class StubMember {
        private final String memberName;
        private final String displayName;
        private final String desc;

        StubMember(String memberName, String displayName, String desc) {
            this.memberName = memberName;
            this.displayName = displayName;
            this.desc = desc != null ? desc : "";
        }

        StubMember(String memberName, String displayName) {
            this(memberName, displayName, "");
        }
    }

    /** Lightweight stand-in for the SQLModel Team row. */
    private static class StubTeam {
        private final String teamName;
        private final String displayName;
        private final String desc;

        StubTeam(String teamName, String displayName, String desc) {
            this.teamName = teamName;
            this.displayName = displayName != null ? displayName : "";
            this.desc = desc != null ? desc : "";
        }

        StubTeam(String teamName, String displayName) {
            this(teamName, displayName, "");
        }
    }

    /**
     * In-memory TeamBackend that tracks call counts.
     * <p>
     * Mirrors the four TeamBackend methods that TeamRail consumes:
     * get_team_updated_at, get_members_max_updated_at, get_team_info, list_members.
     * Lets tests assert that the cache short-circuits expensive calls when the mtime probe is stable.
     */
    private static class FakeTeamBackend {
        private StubTeam team;
        private List<StubMember> members;
        private int teamMtime;
        private int membersMtime;

        int teamMtimeCalls = 0;
        int membersMtimeCalls = 0;
        int getInfoCalls = 0;
        int listMembersCalls = 0;

        FakeTeamBackend(StubTeam team, List<StubMember> members, int teamMtime, int membersMtime) {
            this.team = team;
            this.members = new ArrayList<>(members != null ? members : Collections.emptyList());
            this.teamMtime = teamMtime;
            this.membersMtime = membersMtime;
        }

        FakeTeamBackend(StubTeam team, List<StubMember> members) {
            this(team, members, 1, 1);
        }

        CompletableFuture<Integer> getTeamUpdatedAt() {
            teamMtimeCalls++;
            return CompletableFuture.completedFuture(teamMtime);
        }

        CompletableFuture<Integer> getMembersMaxUpdatedAt() {
            membersMtimeCalls++;
            return CompletableFuture.completedFuture(membersMtime);
        }

        CompletableFuture<StubTeam> getTeamInfo() {
            getInfoCalls++;
            return CompletableFuture.completedFuture(team);
        }

        CompletableFuture<List<StubMember>> listMembers() {
            listMembersCalls++;
            return CompletableFuture.completedFuture(new ArrayList<>(members));
        }

        boolean hittEnabled() {
            // TeamRail probes this; fake teams never enable HITT
            return false;
        }

        Set<String> humanAgentNames() {
            // TeamRail snapshots the roster here; fake teams are empty
            return Collections.emptySet();
        }

        // -- Mutators used by tests --

        void setTeam(StubTeam team, int mtime) {
            this.team = team;
            this.teamMtime = mtime;
        }

        void addMember(StubMember member, int mtime) {
            this.members.add(member);
            this.membersMtime = mtime;
        }
    }

    @Nested
    class TestTeamRailStaticSections {

        @Test
        @Tag("level1")
        void testLeaderRailRegistersStaticSectionsWithoutBackend() {
            SystemPromptBuilder builder = new SystemPromptBuilder("cn");
            StubAgent agent = new StubAgent(builder);

            TeamRail rail = new TeamRail(
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
                    null
            );
            rail.init(agent);
            rail.beforeModelCall(null);

            Map<String, PromptSection> sections = builder.getAllSections();
            assertTrue(sections.containsKey(TeamSectionName.ROLE));
            assertTrue(sections.containsKey(TeamSectionName.WORKFLOW));
            assertTrue(sections.containsKey(TeamSectionName.LIFECYCLE));
            assertTrue(sections.containsKey(TeamSectionName.PERSONA));
            assertTrue(sections.containsKey(TeamSectionName.EXTRA));

            // Without a backend the dynamic sections are skipped entirely
            assertFalse(sections.containsKey(TeamSectionName.INFO));
            assertFalse(sections.containsKey(TeamSectionName.MEMBERS));
        }

        @Test
        @Tag("level1")
        void testTeammateRailOmitsLeaderOnlySections() {
            SystemPromptBuilder builder = new SystemPromptBuilder("cn");
            StubAgent agent = new StubAgent(builder);

            TeamRail rail = new TeamRail(
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
                    null
            );
            rail.init(agent);
            rail.beforeModelCall(null);

            Map<String, PromptSection> sections = builder.getAllSections();
            assertFalse(sections.containsKey(TeamSectionName.WORKFLOW));
            assertFalse(sections.containsKey(TeamSectionName.LIFECYCLE));
            assertFalse(sections.containsKey(TeamSectionName.EXTRA));
            assertTrue(sections.containsKey(TeamSectionName.ROLE));
            assertTrue(sections.containsKey(TeamSectionName.PERSONA));
        }
    }

    @Nested
    class TestTeamRailDynamicSections {

        @Test
        @Tag("level1")
        void testFirstCallLoadsFromDb() {
            FakeTeamBackend backend = new FakeTeamBackend(
                    new StubTeam("Beta", "Test team"),
                    Arrays.asList(
                            new StubMember("leader1", "Leader", "PM"),
                            new StubMember("dev1", "Dev", "Coder")
                    )
            );
            SystemPromptBuilder builder = new SystemPromptBuilder("cn");
            StubAgent agent = new StubAgent(builder);

            TeamRail rail = new TeamRail(
                    TeamRole.LEADER,
                    "PM",
                    "leader1",
                    "temporary",
                    "build_mode",
                    "cn",
                    "default",
                    null,
                    null,
                    null,
                    backend
            );
            rail.init(agent);
            rail.beforeModelCall(null);

            assertTrue(builder.hasSection(TeamSectionName.INFO));
            assertTrue(builder.hasSection(TeamSectionName.MEMBERS));
            assertEquals(1, backend.getInfoCalls);
            assertEquals(1, backend.listMembersCalls);

            // The members section excluded the leader (self exclusion)
            PromptSection membersSection = builder.getSection(TeamSectionName.MEMBERS).orElse(null);
            String membersRender = membersSection.render("cn");
            assertTrue(membersRender.contains("Dev"));
            assertFalse(membersRender.contains("Leader"));
        }

        @Test
        @Tag("level1")
        void testCacheHitSkipsFullQuery() {
            FakeTeamBackend backend = new FakeTeamBackend(
                    new StubTeam("Beta", "Test"),
                    Arrays.asList(new StubMember("dev1", "Dev"))
            );
            SystemPromptBuilder builder = new SystemPromptBuilder("cn");
            StubAgent agent = new StubAgent(builder);

            TeamRail rail = new TeamRail(
                    TeamRole.LEADER,
                    "PM",
                    "leader1",
                    "temporary",
                    "build_mode",
                    "cn",
                    "default",
                    null,
                    null,
                    null,
                    backend
            );
            rail.init(agent);
            rail.beforeModelCall(null);
            rail.beforeModelCall(null);
            rail.beforeModelCall(null);

            // Three model calls, three probes each, but only one full fetch
            assertEquals(3, backend.teamMtimeCalls);
            assertEquals(3, backend.membersMtimeCalls);
            assertEquals(1, backend.getInfoCalls);
            assertEquals(1, backend.listMembersCalls);
        }

        @Test
        @Tag("level1")
        void testCacheMissWhenMemberAdded() {
            FakeTeamBackend backend = new FakeTeamBackend(
                    new StubTeam("Beta", "Test"),
                    Arrays.asList(new StubMember("dev1", "Dev")),
                    1,
                    1
            );
            SystemPromptBuilder builder = new SystemPromptBuilder("cn");
            StubAgent agent = new StubAgent(builder);

            TeamRail rail = new TeamRail(
                    TeamRole.LEADER,
                    "PM",
                    "leader1",
                    "temporary",
                    "build_mode",
                    "cn",
                    "default",
                    null,
                    null,
                    null,
                    backend
            );
            rail.init(agent);
            rail.beforeModelCall(null);

            PromptSection firstMembers = builder.getSection(TeamSectionName.MEMBERS).orElse(null);
            String firstRender = firstMembers.render("cn");
            assertTrue(firstRender.contains("Dev"));
            assertFalse(firstRender.contains("Newbie"));

            // Simulate spawn_member: add a row and bump mtime
            backend.addMember(new StubMember("dev2", "Newbie", "fresh"), 2);
            rail.beforeModelCall(null);

            PromptSection secondMembers = builder.getSection(TeamSectionName.MEMBERS).orElse(null);
            String secondRender = secondMembers.render("cn");
            assertTrue(secondRender.contains("Newbie"));
            assertEquals(2, backend.listMembersCalls);
        }

        @Test
        @Tag("level1")
        void testStatusUpdateDoesNotRefetch() {
            // Status changes don't bump mtime (per design), so the cache holds
            FakeTeamBackend backend = new FakeTeamBackend(
                    new StubTeam("Beta", "Test"),
                    Arrays.asList(new StubMember("dev1", "Dev")),
                    1,
                    42
            );
            SystemPromptBuilder builder = new SystemPromptBuilder("cn");
            StubAgent agent = new StubAgent(builder);

            TeamRail rail = new TeamRail(
                    TeamRole.LEADER,
                    "PM",
                    "leader1",
                    "temporary",
                    "build_mode",
                    "cn",
                    "default",
                    null,
                    null,
                    null,
                    backend
            );
            rail.init(agent);
            rail.beforeModelCall(null);
            // mtime stays at 42 -- a real status update would not bump it
            rail.beforeModelCall(null);
            assertEquals(1, backend.listMembersCalls);
        }

        @Test
        @Tag("level1")
        void testTeamWorkspaceMountPreservedAfterRefresh() {
            FakeTeamBackend backend = new FakeTeamBackend(
                    new StubTeam("Beta", "Test"),
                    Arrays.asList(new StubMember("dev1", "Dev"))
            );
            SystemPromptBuilder builder = new SystemPromptBuilder("cn");
            StubAgent agent = new StubAgent(builder);

            TeamRail rail = new TeamRail(
                    TeamRole.LEADER,
                    "PM",
                    "leader1",
                    "temporary",
                    "build_mode",
                    "cn",
                    "default",
                    null,
                    ".team/beta/",
                    "/abs/team-workspace",
                    backend
            );
            rail.init(agent);
            rail.beforeModelCall(null);

            PromptSection firstInfo = builder.getSection(TeamSectionName.INFO).orElse(null);
            String first = firstInfo.render("cn");
            assertTrue(first.contains(".team/beta/"));

            // Trigger a roster refresh (members mtime bump)
            backend.setTeam(new StubTeam("Beta-renamed", "Test"), 99);
            rail.beforeModelCall(null);

            PromptSection secondInfo = builder.getSection(TeamSectionName.INFO).orElse(null);
            String second = secondInfo.render("cn");
            assertTrue(second.contains(".team/beta/"));
            assertTrue(second.contains("Beta-renamed"));
        }

        @Test
        @Tag("level1")
        void testPriorityOrderInBuiltPrompt() {
            FakeTeamBackend backend = new FakeTeamBackend(
                    new StubTeam("T1", "D"),
                    Arrays.asList(new StubMember("dev1", "D"))
            );
            SystemPromptBuilder builder = new SystemPromptBuilder("cn");
            StubAgent agent = new StubAgent(builder);

            TeamRail rail = new TeamRail(
                    TeamRole.LEADER,
                    "PM",
                    "leader1",
                    "temporary",
                    "build_mode",
                    "cn",
                    "default",
                    null,
                    null,
                    null,
                    backend
            );
            rail.init(agent);
            rail.beforeModelCall(null);

            String prompt = builder.build();
            int idxRole = prompt.indexOf("# 团队角色");
            int idxWorkflow = prompt.indexOf("# 工作流程");
            int idxLifecycle = prompt.indexOf("# 团队生命周期");
            int idxPersona = prompt.indexOf("# 当前人设");
            int idxInfo = prompt.indexOf("# 团队信息");
            int idxMembers = prompt.indexOf("# 成员关系");

            assertTrue(idxRole < idxWorkflow);
            assertTrue(idxWorkflow < idxLifecycle);
            assertTrue(idxLifecycle < idxPersona);
            assertTrue(idxPersona < idxInfo);
            assertTrue(idxInfo < idxMembers);
        }

        @Test
        @Tag("level1")
        void testUninitRemovesStaticAndDynamicSections() {
            FakeTeamBackend backend = new FakeTeamBackend(
                    new StubTeam("T", "D"),
                    Arrays.asList(new StubMember("dev1", "Dev"))
            );
            SystemPromptBuilder builder = new SystemPromptBuilder("cn");
            StubAgent agent = new StubAgent(builder);

            TeamRail rail = new TeamRail(
                    TeamRole.LEADER,
                    "PM",
                    "leader1",
                    "temporary",
                    "build_mode",
                    "cn",
                    "default",
                    null,
                    null,
                    null,
                    backend
            );
            rail.init(agent);
            rail.beforeModelCall(null);

            assertTrue(builder.hasSection(TeamSectionName.ROLE));
            assertTrue(builder.hasSection(TeamSectionName.INFO));
            assertTrue(builder.hasSection(TeamSectionName.MEMBERS));

            rail.uninit(agent);

            assertFalse(builder.hasSection(TeamSectionName.ROLE));
            assertFalse(builder.hasSection(TeamSectionName.WORKFLOW));
            assertFalse(builder.hasSection(TeamSectionName.LIFECYCLE));
            assertFalse(builder.hasSection(TeamSectionName.PERSONA));
            assertFalse(builder.hasSection(TeamSectionName.INFO));
            assertFalse(builder.hasSection(TeamSectionName.MEMBERS));
        }
    }
}
