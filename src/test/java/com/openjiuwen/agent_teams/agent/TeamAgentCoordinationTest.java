package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.message.MessageRecord;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_team_agent_coordination.py}.
 */
class TeamAgentCoordinationTest {

    @Test
    void coordinationLoopIsCreatedDuringConfigure() {
        CapturingTeamAgent agent = createLeader();

        assertNotNull(agent.getCoordinatorLoop());
        assertEquals(TeamRole.LEADER, agent.getCoordinatorLoop().getRole());
    }

    @Test
    void validMentionRoutesDirectMessageWithoutInvokingLeader() {
        CapturingTeamAgent agent = createLeader();
        agent.spawnMember(member("dev-1", "Developer", "backend dev"), null);

        agent.receiveUserInput("@dev-1 请完成这个任务");

        List<MessageRecord> inbox = agent.getTeamBackend().getMessages("dev-1", false, null);
        assertEquals(1, inbox.size());
        assertEquals("请完成这个任务", inbox.get(0).getContent());
        assertTrue(agent.getCapturedLeaderInputs().isEmpty());
    }

    @Test
    void invalidMentionFallsBackToLeaderFlow() {
        CapturingTeamAgent agent = createLeader();
        agent.spawnMember(member("dev-1", "Developer", "backend dev"), null);

        agent.receiveUserInput("@nonexistent hello");

        assertEquals(List.of("@nonexistent hello"), agent.getCapturedLeaderInputs());
    }

    @Test
    void plainMessageUsesLeaderFlow() {
        CapturingTeamAgent agent = createLeader();

        agent.receiveUserInput("普通消息");

        assertEquals(List.of("普通消息"), agent.getCapturedLeaderInputs());
    }

    @Test
    void mentionWithoutBodyFallsBackToLeaderFlow() {
        CapturingTeamAgent agent = createLeader();
        agent.spawnMember(member("dev-1", "Developer", "backend dev"), null);

        agent.receiveUserInput("@dev-1");

        assertEquals(List.of("@dev-1"), agent.getCapturedLeaderInputs());
    }

    private static CapturingTeamAgent createLeader() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("test-team");

        LeaderSpec leader = new LeaderSpec();
        leader.setMemberName("leader-1");
        leader.setDisplayName("Leader");
        leader.setPersona("PM");
        spec.setLeader(leader);

        DeepAgentConfig config = new DeepAgentConfig();
        config.setSystemPrompt("You are the team leader.");
        DeepAgentSpec deepAgentSpec = new DeepAgentSpec();
        deepAgentSpec.setConfig(config);
        spec.getAgents().put("leader", deepAgentSpec);

        CapturingTeamAgent agent = new CapturingTeamAgent(card("leader-1", "leader", "test"));
        agent.configure(spec);
        return agent;
    }

    private static TeamMemberSpec member(String memberName, String displayName, String persona) {
        TeamMemberSpec spec = new TeamMemberSpec();
        spec.setMemberName(memberName);
        spec.setDisplayName(displayName);
        spec.setRoleType(TeamRole.TEAMMATE);
        spec.setPersona(persona);
        spec.setPromptHint("Do the work");
        return spec;
    }

    private static AgentCard card(String id, String name, String description) {
        AgentCard card = new AgentCard();
        card.setId(id);
        card.setName(name);
        card.setDescription(description);
        return card;
    }

    private static final class CapturingTeamAgent extends TeamAgent {
        private final List<String> capturedLeaderInputs = new ArrayList<>();

        private CapturingTeamAgent(AgentCard card) {
            super(card);
        }

        @Override
        public Object deliverInput(Object content) {
            capturedLeaderInputs.add(String.valueOf(content));
            return content;
        }

        private List<String> getCapturedLeaderInputs() {
            return capturedLeaderInputs;
        }
    }
}
