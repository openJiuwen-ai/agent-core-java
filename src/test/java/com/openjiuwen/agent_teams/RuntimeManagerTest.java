package com.openjiuwen.agent_teams;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamLifecycle;
import com.openjiuwen.harness.DeepAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void pauseAndInteractRouteOnlyMatchingActiveRuntime() {
        RuntimeManager manager = new RuntimeManager();
        RecordingSpec spec = new RecordingSpec("runtime-control-team");

        manager.activate(spec, "runtime-control-session", Map.of("query", "start")).join();

        assertFalse(manager.interact("wrong team", "other-team", "runtime-control-session").join());
        assertFalse(manager.interact("wrong session", "runtime-control-team", "other-session").join());
        assertTrue(manager.interact("follow-up", "runtime-control-team", "runtime-control-session").join());
        assertEquals(List.of("follow-up"), spec.agent.interactions);

        assertFalse(manager.pause("runtime-control-team", "other-session").join());
        assertEquals(0, spec.agent.pauseCalls);

        assertTrue(manager.pause("runtime-control-team", "runtime-control-session").join());
        assertTrue(manager.isPaused());
        assertEquals(1, spec.agent.pauseCalls);

        RuntimeManager.TeamRuntimeActivation resumed =
                manager.activate(spec, "runtime-control-session", Map.of("query", "resume")).join();

        assertSame(spec.agent, resumed.getAgent());
        assertEquals("resume_paused", resumed.getActivationKind());
        assertFalse(manager.isPaused());
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

    public static class RecordingSpec {
        private final String teamName;
        private final RecordingAgent agent = new RecordingAgent();

        RecordingSpec(String teamName) {
            this.teamName = teamName;
        }

        public String getTeamName() {
            return teamName;
        }

        public RecordingAgent build() {
            return agent;
        }
    }

    public static class RecordingAgent {
        private final List<String> interactions = new ArrayList<>();
        private int pauseCalls;

        public void interact(String message) {
            interactions.add(message);
        }

        public void pauseCoordination() {
            pauseCalls++;
        }
    }
}
