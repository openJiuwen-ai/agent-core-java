package com.openjiuwen.agent_teams.monitor;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.spawn.SpawnContext;
import com.openjiuwen.harness.DeepAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mirrors Python's {@code openjiuwen.agent_teams.monitor.team_monitor}.
 */
class TeamMonitorTest {

    @Test
    void monitorRegistersListenerAndQueriesBackendState() {
        TeamAgent agent = TeamAgent.fromSpec(createSpec());
        TeamMemberSpec worker = new TeamMemberSpec();
        worker.setMemberName("worker");
        worker.setDisplayName("Worker");
        worker.setRoleType(TeamRole.TEAMMATE);
        agent.spawnMember(worker, null);
        agent.getTeamBackend().createTask("Plan", "Do the work", "task-1", List.of());
        agent.getTeamBackend().sendMessage("hello", "worker", "leader");

        TeamMonitor monitor = TeamMonitor.createMonitor(agent);

        TeamInfo teamInfo = monitor.getTeamInfo().join();
        assertEquals("monitor-team", teamInfo.getTeamId());
        assertEquals(1, monitor.getMembers(null).join().size());
        assertEquals("worker", monitor.getMember("worker").join().getMemberId());
        assertEquals(1, monitor.getTasks(null).join().size());
        assertEquals(1, monitor.getMessages("worker", null).join().size());

        monitor.start();
        assertEquals(1, agent.getEventListeners().size());

        agent.notifyEvent("member_spawned", Map.of("team_name", "monitor-team", "member_name", "worker"));
        MonitorEvent event = monitor.nextEvent();
        assertNotNull(event);
        assertEquals("member_spawned", event.getEventType());
        assertEquals("monitor-team", event.getTeamId());

        monitor.stop();
        assertFalse(monitor.hasNextEvent());
        assertEquals(0, agent.getEventListeners().size());
        assertNull(monitor.nextEvent());
    }

    @Test
    void createMonitorUsesCurrentSessionContext() {
        TeamAgent agent = TeamAgent.fromSpec(createSpec());

        try {
            SpawnContext.setSessionId("session-42");

            TeamMonitor monitor = TeamMonitor.createMonitor(agent);

            assertEquals("session-42", monitor.getSessionId());
        } finally {
            SpawnContext.resetSessionId();
        }
    }

    @Test
    void getMessagesWithoutRecipientKeepsTeamMessageTimestampOrder() throws InterruptedException {
        TeamAgentSpec spec = createSpec();
        spec.setTeamName("monitor-team-order");
        TeamAgent agent = TeamAgent.fromSpec(spec);
        TeamMemberSpec worker = new TeamMemberSpec();
        worker.setMemberName("worker");
        worker.setDisplayName("Worker");
        worker.setRoleType(TeamRole.TEAMMATE);
        agent.spawnMember(worker, null);
        agent.getTeamBackend().broadcastMessage("first broadcast", "leader");
        Thread.sleep(2L);
        agent.getTeamBackend().sendMessage("second direct", "worker", "leader");

        TeamMonitor monitor = TeamMonitor.createMonitor(agent);
        List<MessageInfo> messages = monitor.getMessages(null, null).join();

        assertEquals(2, messages.size());
        assertEquals("first broadcast", messages.get(0).getContent());
        assertEquals("second direct", messages.get(1).getContent());
    }

    private static TeamAgentSpec createSpec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("monitor-team");

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
