package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamLifecycle;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.DeepAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PersistentTeamSessionRecoveryTest {

    @Test
    void resumeForNewSessionPersistsLeaderStateAndRebindsLiveMember() {
        TeamAgent agent = TeamAgent.fromSpec(createPersistentSpec());
        TeamMemberSpec memberSpec = createMemberSpec("worker_a");
        agent.spawnMember(memberSpec, null);
        agent.getTeamBackend().getMember("worker_a").setStatus(MemberStatus.BUSY);
        agent.getTeamBackend().getMember("worker_a").setExecutionStatus(ExecutionStatus.RUNNING);
        agent.getTeamBackend().ensureMemberRuntime("worker_a");

        AgentSessionApi session = AgentSessionApi.create("session-new", Map.of(), null);
        agent.resumeForNewSession(session);

        Object leaderStateRaw = session.getState(RecoveryManager.LEADER_STATE_KEY);
        Map<?, ?> leaderState = assertInstanceOf(Map.class, leaderStateRaw);
        assertEquals("persistent-team", leaderState.get("team_name"));

        Object recoverableRaw = session.getState(RecoveryManager.RECOVERABLE_MEMBERS_KEY);
        List<?> recoverable = assertInstanceOf(List.class, recoverableRaw);
        assertEquals(1, recoverable.size());
        Map<?, ?> entry = assertInstanceOf(Map.class, recoverable.get(0));
        assertEquals("worker_a", entry.get("member_name"));
        assertEquals("READY", entry.get("status"));
        assertEquals(MemberStatus.READY, agent.getTeamBackend().getMember("worker_a").getStatus());
        assertNotNull(agent.getTeamBackend().getMemberSession("worker_a"));
        assertNotNull(agent.getTeamBackend().getMemberRuntime("worker_a"));
    }

    @Test
    void recoverForExistingSessionRestoresSavedLeaderMetadataAndRecoverableMembers() {
        TeamAgent agent = TeamAgent.fromSpec(createPersistentSpec());
        TeamMemberSpec memberSpec = createMemberSpec("worker_a");
        agent.spawnMember(memberSpec, null);

        AgentSessionApi session = AgentSessionApi.create("session-existing", Map.of(), null);
        Map<String, Object> savedLeaderState = new LinkedHashMap<>();
        savedLeaderState.put("team_name", "restored-team");
        savedLeaderState.put("language", "zh-CN");
        savedLeaderState.put("metadata", Map.of("resume", true));
        savedLeaderState.put("leader", Map.of("member_name", "leader", "persona", "Recovered leader"));
        savedLeaderState.put("context", Map.of(
                "member_name", "leader",
                "persona", "Recovered leader",
                "metadata", Map.of("resume", true),
                "team_spec", Map.of(
                        "team_name", "restored-team",
                        "display_name", "restored-team",
                        "leader_member_name", "leader",
                        "language", "zh-CN",
                        "metadata", Map.of("resume", true)
                )
        ));
        session.updateState(Map.of(
                RecoveryManager.LEADER_STATE_KEY, savedLeaderState,
                RecoveryManager.RECOVERABLE_MEMBERS_KEY, List.of(Map.of("member_name", "worker_a", "status", "BUSY"))
        ));

        agent.recoverForExistingSession(session);

        assertEquals("restored-team", agent.getSpec().getTeamName());
        assertEquals("zh-CN", agent.getSpec().getLanguage());
        assertEquals(MemberStatus.BUSY, agent.getTeamBackend().getMember("worker_a").getStatus());
        assertNotNull(agent.getTeamBackend().getMemberSession("worker_a"));
    }

    private TeamAgentSpec createPersistentSpec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("persistent-team");
        spec.setLifecycle(TeamLifecycle.PERSISTENT);
        spec.setLanguage("en");

        LeaderSpec leader = new LeaderSpec();
        leader.setMemberName("leader");
        leader.setPersona("Lead the team");
        spec.setLeader(leader);

        DeepAgentConfig deepAgentConfig = new DeepAgentConfig();
        deepAgentConfig.setSystemPrompt("You are the leader.");
        DeepAgentSpec leaderAgentSpec = new DeepAgentSpec();
        leaderAgentSpec.setConfig(deepAgentConfig);
        leaderAgentSpec.setLanguage("en");
        spec.getAgents().put("leader", leaderAgentSpec);
        spec.setMetadata(new LinkedHashMap<>(Map.of("created_by", "test")));
        return spec;
    }

    private TeamMemberSpec createMemberSpec(String memberName) {
        TeamMemberSpec memberSpec = new TeamMemberSpec();
        memberSpec.setMemberName(memberName);
        memberSpec.setDisplayName(memberName);
        memberSpec.setRoleType(TeamRole.TEAMMATE);
        memberSpec.setPersona("Worker");
        memberSpec.setPromptHint("Do work");
        return memberSpec;
    }
}
