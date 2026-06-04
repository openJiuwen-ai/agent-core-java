package com.openjiuwen.agent_teams.spawn;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.TeamRuntimeContext;
import com.openjiuwen.agent_teams.schema.TeamSpec;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code inprocess_spawn} in
 * {@code openjiuwen.agent_teams.spawn.inprocess_spawn}.
 */
class InProcessSpawnTest {

    private static final String DEFAULT_QUERY = "Join the team and wait for your first assignment.";

    @Test
    void emptyInitialMessageFallsBackToDefaultQuery() {
        RecordingTeamAgent leader = recordingLeader("spawn-empty-message");
        TeamRuntimeContext ctx = teammateContext(leader.getSpec().getTeamName(), "worker-a");

        InProcessHandle handle = InProcessSpawn.inprocessSpawn(leader, ctx, "", "session-empty-message");

        assertEquals(0, handle.waitForCompletion());
        assertEquals("worker-a", leader.lastMember.get());
        assertEquals(DEFAULT_QUERY, leader.lastContent.get());
        assertEquals("inproc-worker-a", handle.getProcessId());
    }

    @Test
    void whitespaceSessionIdPropagatesLikePythonTruthyString() {
        RecordingTeamAgent leader = recordingLeader("spawn-whitespace-session");
        TeamRuntimeContext ctx = teammateContext(leader.getSpec().getTeamName(), "worker-b");

        InProcessHandle handle = InProcessSpawn.inprocessSpawn(leader, ctx, "start", "   ");

        assertEquals(0, handle.waitForCompletion());
        assertEquals("   ", leader.sessionSeenByMember.get());
    }

    private static RecordingTeamAgent recordingLeader(String prefix) {
        RecordingTeamAgent agent = new RecordingTeamAgent();
        agent.configure(createSpec(prefix + "-" + UUID.randomUUID().toString().replace("-", "")));
        return agent;
    }

    private static TeamAgentSpec createSpec(String teamName) {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName(teamName);

        LeaderSpec leader = new LeaderSpec();
        leader.setMemberName("leader");
        spec.setLeader(leader);

        DeepAgentConfig config = new DeepAgentConfig();
        config.setSystemPrompt("Lead.");
        AgentCard card = new AgentCard();
        card.setName("leader-card");
        config.setCard(card);

        DeepAgentSpec leaderAgent = new DeepAgentSpec();
        leaderAgent.setConfig(config);
        spec.setAgents(Map.of("leader", leaderAgent));
        return spec;
    }

    private static TeamRuntimeContext teammateContext(String teamName, String memberName) {
        TeamSpec teamSpec = new TeamSpec();
        teamSpec.setTeamName(teamName);
        teamSpec.setDisplayName(teamName);
        teamSpec.setLeaderMemberName("leader");

        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(TeamRole.TEAMMATE);
        ctx.setMemberName(memberName);
        ctx.setPersona("Build the assigned feature");
        ctx.setTeamSpec(teamSpec);
        return ctx;
    }

    private static final class RecordingTeamAgent extends TeamAgent {
        private final AtomicReference<String> lastMember = new AtomicReference<>();
        private final AtomicReference<Object> lastContent = new AtomicReference<>();
        private final AtomicReference<String> sessionSeenByMember = new AtomicReference<>();

        private RecordingTeamAgent() {
            super(new AgentCard());
        }

        @Override
        public Object runMember(String memberName, Object content) {
            lastMember.set(memberName);
            lastContent.set(content);
            sessionSeenByMember.set(SpawnContext.getSessionId());
            return Map.of("member", memberName, "query", content);
        }
    }
}
