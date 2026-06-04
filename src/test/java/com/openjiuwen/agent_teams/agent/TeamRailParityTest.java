package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Executable parity coverage for TeamRail.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.test_team_rail}
 * and the TeamRail-specific HITT assertions in {@code test_hitt}.</p>
 */
class TeamRailParityTest {

    @Test
    void leaderRoleSectionIncludesModeMemberAndPolicy() {
        PromptSection section = TeamRail.buildTeamRoleSection(TeamRole.LEADER, "leader1", "build_mode", "cn");

        assertNotNull(section);
        assertEquals(TeamSectionName.ROLE, section.getName());
        assertEquals(11, section.getPriority());
        String content = section.render("cn");
        assertTrue(content.contains("# 团队角色"));
        assertTrue(content.contains("你的 member_name: leader1"));
        assertTrue(content.contains("build_mode"));
        assertTrue(content.contains("create_task"));
    }

    @Test
    void teammateRoleSectionIncludesOwnModeAndPolicy() {
        PromptSection section = TeamRail.buildTeamRoleSection(TeamRole.TEAMMATE, "dev1", "plan_mode", "cn");

        assertNotNull(section);
        String content = section.render("cn");
        assertTrue(content.contains("你的执行模式: plan_mode"));
        assertTrue(content.contains("view_task"));
    }

    @Test
    void leaderOnlySectionsReturnNullForTeammate() {
        assertNull(TeamRail.buildTeamWorkflowSection(TeamRole.TEAMMATE, "default", "cn"));
        assertNull(TeamRail.buildTeamLifecycleSection(TeamRole.TEAMMATE, "temporary", "cn"));
    }

    @Test
    void personaAndExtraEmptyInputsReturnNull() {
        assertNull(TeamRail.buildTeamPersonaSection("", "cn"));
        assertNull(TeamRail.buildTeamPersonaSection(null, "cn"));
        assertNull(TeamRail.buildTeamExtraSection(null, "cn"));
        assertNull(TeamRail.buildTeamExtraSection("   ", "cn"));
    }

    @Test
    void teamInfoIncludesWorkspaceMountPurposeAndAbsolutePath() {
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
        assertEquals(65, section.getPriority());
        String content = section.render("cn");
        assertTrue(content.contains("# 团队信息"));
        assertTrue(content.contains("AlphaTeam"));
        assertTrue(content.contains("`.team/alpha/`"));
        assertTrue(content.contains("系统自动管理版本"));
        assertTrue(content.contains("`/abs/team-workspace`"));
    }

    @Test
    void teamInfoEmptyDataReturnsNull() {
        assertNull(TeamRail.buildTeamInfoSection(null, null, null, "cn"));
        assertNull(TeamRail.buildTeamInfoSection(Map.of("unrelated", "value"), null, null, "cn"));
    }

    @Test
    void membersSectionExcludesSelfAndReturnsNullWhenOnlySelf() {
        List<Map<String, String>> members = new ArrayList<>();
        members.add(Map.of("member_name", "leader1", "display_name", "Leader", "desc", "PM"));
        members.add(Map.of("member_name", "dev1", "display_name", "Dev", "desc", "Coder"));

        PromptSection section = TeamRail.buildTeamMembersSection(members, "leader1", "cn");

        assertNotNull(section);
        String content = section.render("cn");
        assertTrue(content.contains("Dev"));
        assertFalse(content.contains("Leader"));
        assertNull(TeamRail.buildTeamMembersSection(List.of(Map.of("member_name", "self")), "self", "cn"));
    }

    @Test
    void hittSectionLeaderMentionsHumanLockRules() {
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
    void hittSectionHumanAgentDescribesConstrainedTools() {
        PromptSection section = TeamRail.buildTeamHittSection(
                TeamRole.HUMAN_AGENT,
                List.of("human_agent"),
                "en",
                "human_agent"
        );

        assertNotNull(section);
        String body = section.render("en");
        assertTrue(body.contains("Your member_name is `human_agent`"));
        assertTrue(body.contains("send_message"));
        assertTrue(body.contains("claim_task"));
    }

    @Test
    void hittSectionListsEveryHumanMemberAndSkipsEmptyRoster() {
        assertNull(TeamRail.buildTeamHittSection(TeamRole.LEADER, Collections.emptyList(), "cn", null));

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

    @Test
    void railRegistersStaticSectionsWithoutBackend() {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
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

        rail.init(new StubAgent(builder));
        rail.beforeModelCall((Object) null).join();

        Map<String, PromptSection> sections = builder.getAllSections();
        assertTrue(sections.containsKey(TeamSectionName.ROLE));
        assertTrue(sections.containsKey(TeamSectionName.WORKFLOW));
        assertTrue(sections.containsKey(TeamSectionName.LIFECYCLE));
        assertTrue(sections.containsKey(TeamSectionName.PERSONA));
        assertTrue(sections.containsKey(TeamSectionName.EXTRA));
        assertFalse(sections.containsKey(TeamSectionName.INFO));
        assertFalse(sections.containsKey(TeamSectionName.MEMBERS));
    }

    @Test
    void dynamicSectionsUseMtimeCacheAndRefreshWhenMembersChange() {
        FakeTeamBackend backend = new FakeTeamBackend(
                new StubTeam("Beta", "Test team", "goal"),
                List.of(new StubMember("leader1", "Leader", "PM"), new StubMember("dev1", "Dev", "Coder"))
        );
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
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

        rail.init(new StubAgent(builder));
        rail.beforeModelCall((Object) null).join();
        rail.beforeModelCall((Object) null).join();

        assertEquals(2, backend.teamMtimeCalls);
        assertEquals(2, backend.membersMtimeCalls);
        assertEquals(1, backend.getInfoCalls);
        assertEquals(1, backend.listMembersCalls);
        assertTrue(builder.getSection(TeamSectionName.MEMBERS).orElseThrow().render("cn").contains("Dev"));

        backend.addMember(new StubMember("dev2", "Newbie", "fresh"), 2);
        rail.beforeModelCall((Object) null).join();

        assertEquals(2, backend.listMembersCalls);
        assertTrue(builder.getSection(TeamSectionName.MEMBERS).orElseThrow().render("cn").contains("Newbie"));
    }

    private static final class StubAgent {
        private final SystemPromptBuilder systemPromptBuilder;

        private StubAgent(SystemPromptBuilder systemPromptBuilder) {
            this.systemPromptBuilder = systemPromptBuilder;
        }

        public SystemPromptBuilder getSystemPromptBuilder() {
            return systemPromptBuilder;
        }
    }

    private static final class StubTeam {
        private final String teamName;
        private final String displayName;
        private final String desc;

        private StubTeam(String teamName, String displayName, String desc) {
            this.teamName = teamName;
            this.displayName = displayName;
            this.desc = desc;
        }
    }

    private static final class StubMember {
        private final String memberName;
        private final String displayName;
        private final String desc;

        private StubMember(String memberName, String displayName, String desc) {
            this.memberName = memberName;
            this.displayName = displayName;
            this.desc = desc;
        }
    }

    private static final class FakeTeamBackend {
        private StubTeam team;
        private final List<StubMember> members;
        private int teamMtime = 1;
        private int membersMtime = 1;
        private int teamMtimeCalls;
        private int membersMtimeCalls;
        private int getInfoCalls;
        private int listMembersCalls;

        private FakeTeamBackend(StubTeam team, List<StubMember> members) {
            this.team = team;
            this.members = new ArrayList<>(members);
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

        CompletableFuture<List<String>> humanAgentNames() {
            return CompletableFuture.completedFuture(List.of());
        }

        void addMember(StubMember member, int mtime) {
            members.add(member);
            membersMtime = mtime;
        }
    }
}
