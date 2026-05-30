package com.openjiuwen.agent_teams;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamLifecycle;
import com.openjiuwen.harness.DeepAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's {@code openjiuwen.agent_teams.runtime_manager}.
 */
class RuntimeManagerTest {

    @Test
    void activateCreatesAndResumesPersistentTeamSessions() {
        RuntimeManager manager = new RuntimeManager();
        TeamAgentSpec spec = createSpec();

        RuntimeManager.TeamRuntimeActivation created =
                manager.activate(spec, "runtime-session-1", Map.of("query", "start")).join();

        TeamAgent agent = assertInstanceOf(TeamAgent.class, created.getAgent());
        assertEquals("create", created.getActivationKind());
        assertEquals("runtime-session-1", manager.getActiveSessionId().orElseThrow());

        RuntimeManager.TeamRuntimeActivation resumed =
                manager.activate(spec, "runtime-session-2", Map.of("query", "resume")).join();

        assertSame(agent, resumed.getAgent());
        assertEquals("resume", resumed.getActivationKind());
        assertEquals("runtime-session-2", manager.getActiveSessionId().orElseThrow());
    }

    private static TeamAgentSpec createSpec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("runtime-team");
        spec.setLifecycle(TeamLifecycle.PERSISTENT);

        LeaderSpec leader = new LeaderSpec();
        leader.setMemberName("leader");
        spec.setLeader(leader);

        DeepAgentConfig config = new DeepAgentConfig();
        config.setSystemPrompt("Lead.");
        DeepAgentSpec leaderAgent = new DeepAgentSpec();
        leaderAgent.setConfig(config);
        spec.getAgents().put("leader", leaderAgent);
        return spec;
    }
}
