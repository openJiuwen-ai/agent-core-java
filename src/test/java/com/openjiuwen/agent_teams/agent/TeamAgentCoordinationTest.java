package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

        agent.receiveUserInput("@dev-1 finish the task");

        assertEquals(
                List.of("finish the task"),
                agent.getTeamBackend().getMessages("dev-1", false, null).stream().map(m -> m.getContent()).toList()
        );
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

        agent.receiveUserInput("plain message");

        assertEquals(List.of("plain message"), agent.getCapturedLeaderInputs());
    }

    @Test
    void mentionWithoutBodyFallsBackToLeaderFlow() {
        CapturingTeamAgent agent = createLeader();
        agent.spawnMember(member("dev-1", "Developer", "backend dev"), null);

        agent.receiveUserInput("@dev-1");

        assertEquals(List.of("@dev-1"), agent.getCapturedLeaderInputs());
    }

    @Test
    void startCoordinationMarksLoopRunning() {
        CapturingTeamAgent agent = createLeader();

        try {
            agent.startCoordination();

            assertTrue(agent.getCoordinatorLoop().isRunning());
        } finally {
            agent.stopCoordination();
        }
    }

    @Test
    void stopCoordinationMarksLoopStopped() {
        CapturingTeamAgent agent = createLeader();

        agent.startCoordination();
        agent.stopCoordination();

        assertFalse(agent.getCoordinatorLoop().isRunning());
    }

    @Test
    void interactDispatchesUserInputWhenLoopIsRunning() {
        CapturingTeamAgent agent = createStartedLeader();

        try {
            agent.interact("coordinate this");

            assertEquals(List.of("User input for team test-team: coordinate this"), agent.getCapturedLeaderInputs());
        } finally {
            agent.stopCoordination();
        }
    }

    @Test
    void notifyEventPublishesEventMessageToListeners() {
        CapturingTeamAgent agent = createLeader();
        AtomicReference<EventMessage> seen = new AtomicReference<>();
        agent.addEventListener(event -> seen.set((EventMessage) event));

        agent.notifyEvent("task_created", Map.of("task_id", "task-1", "title", "Fix bug"));

        EventMessage message = seen.get();
        assertNotNull(message);
        assertEquals("task_created", message.getEventType());
        assertEquals("task-1", message.getPayload().get("task_id"));
        assertEquals("Fix bug", message.getPayload().get("title"));
    }

    @Test
    void taskCreatedEventProducesSummary() {
        assertEventSummary(
                "task_created",
                Map.of("task_id", "task-1", "title", "Fix bug"),
                "Task created in team test-team: #task-1 Fix bug"
        );
    }

    @Test
    void taskUpdatedEventProducesSummary() {
        assertEventSummary(
                "task_updated",
                Map.of("task_id", "task-1"),
                "Task updated in team test-team: #task-1"
        );
    }

    @Test
    void taskClaimedEventProducesSummary() {
        assertEventSummary(
                "task_claimed",
                Map.of("task_id", "task-1", "member_name", "dev-1"),
                "Task claimed in team test-team: #task-1 by dev-1"
        );
    }

    @Test
    void taskCompletedEventProducesSummary() {
        assertEventSummary(
                "task_completed",
                Map.of("task_id", "task-1"),
                "Task completed in team test-team: #task-1"
        );
    }

    @Test
    void taskCancelledEventProducesSummary() {
        assertEventSummary(
                "task_cancelled",
                Map.of("task_id", "task-1"),
                "Task cancelled in team test-team: #task-1"
        );
    }

    @Test
    void taskUnblockedEventProducesSummary() {
        assertEventSummary(
                "task_unblocked",
                Map.of("task_id", "task-1"),
                "Task unblocked in team test-team: #task-1"
        );
    }

    @Test
    void memberSpawnedEventProducesSummary() {
        assertEventSummary(
                "member_spawned",
                Map.of("member_name", "dev-1"),
                "Member spawned in team test-team: dev-1"
        );
    }

    @Test
    void memberRestartedEventProducesSummary() {
        assertEventSummary(
                "member_restarted",
                Map.of("member_name", "dev-1", "reason", "session switch"),
                "Member restarted in team test-team: dev-1 reason=session switch"
        );
    }

    @Test
    void memberStatusChangedEventProducesSummary() {
        assertEventSummary(
                "member_status_changed",
                Map.of("member_name", "dev-1", "old_status", "busy", "new_status", "ready"),
                "Member status changed in team test-team: dev-1 busy -> ready"
        );
    }

    @Test
    void memberExecutionChangedEventProducesSummary() {
        assertEventSummary(
                "member_execution_changed",
                Map.of("member_name", "dev-1", "old_status", "running", "new_status", "idle"),
                "Member execution changed in team test-team: dev-1 running -> idle"
        );
    }

    @Test
    void memberShutdownEventProducesSummary() {
        assertEventSummary(
                "member_shutdown",
                Map.of("member_name", "dev-1"),
                "Member shutdown in team test-team: dev-1"
        );
    }

    @Test
    void memberCanceledEventProducesSummary() {
        assertEventSummary(
                "member_canceled",
                Map.of("member_name", "dev-1"),
                "Member canceled in team test-team: dev-1"
        );
    }

    @Test
    void toolApprovalResultEventProducesSummary() {
        assertEventSummary(
                "tool_approval_result",
                Map.of("member_name", "dev-1", "approved", true),
                "Tool approval result in team test-team: member=dev-1 approved=true"
        );
    }

    @Test
    void standbyEventProducesSummary() {
        assertEventSummary("team_standby", Map.of(), "Team test-team entered standby.");
    }

    @Test
    void cleanedEventProducesSummary() {
        assertEventSummary("team_cleaned", Map.of(), "Team test-team cleaned.");
    }

    @Test
    void shutdownEventProducesSummary() {
        assertEventSummary("shutdown", Map.of(), "Shutdown requested for team test-team.");
    }

    @Test
    void taskPollSummaryReflectsPendingClaimedAndBlockedCounts() {
        CapturingTeamAgent agent = createStartedLeader();
        agent.spawnMember(member("dev-1", "Developer", "backend dev"), null);
        agent.getTeamBackend().createTask("Claimed task", "active work", "task-1", List.of());
        agent.getTeamBackend().createTask("Blocked task", "waiting work", "task-2", List.of("task-1"));
        agent.getTeamBackend().createTask("Pending task", "queued work", "task-3", List.of());
        assertTrue(agent.getTeamBackend().claimTask("task-1", "dev-1"));

        try {
            agent.notifyEvent("coordination_poll_task", Map.of());

            assertEquals(
                    "Task poll for team test-team: total=3, pending=1, claimed=1, blocked=1.",
                    lastCaptured(agent)
            );
        } finally {
            agent.stopCoordination();
        }
    }

    @Test
    void mailboxPollSummaryReflectsUnreadDirectAndBroadcastMessages() {
        CapturingTeamAgent agent = createStartedLeader();
        agent.spawnMember(member("dev-1", "Developer", "backend dev"), null);
        agent.getTeamBackend().sendMessage("direct note", "leader-1", "dev-1");
        agent.getTeamBackend().broadcastMessage("broadcast note", "dev-1");

        try {
            agent.notifyEvent("coordination_poll_mailbox", Map.of());

            assertEquals(
                    "Mailbox poll for leader-1 in team test-team: unread_direct=1, unread_broadcast=1.",
                    lastCaptured(agent)
            );
        } finally {
            agent.stopCoordination();
        }
    }

    @Test
    void firstIterationGateIsRegisteredByIdentity() {
        CapturingTeamAgent agent = createLeader();

        AgentRail registeredGate = agent.getRegisteredRails().stream()
                .filter(FirstIterationGate.class::isInstance)
                .findFirst()
                .orElseThrow();

        assertSame(agent.getFirstIterationGate(), registeredGate);
    }

    @Test
    void teamRailIsRegisteredAlongsideFirstIterationGate() {
        CapturingTeamAgent agent = createLeader();

        assertEquals(2, agent.getRegisteredRails().size());
        assertTrue(agent.getRegisteredRails().stream().anyMatch(FirstIterationGate.class::isInstance));
        assertTrue(agent.getRegisteredRails().stream().anyMatch(TeamRail.class::isInstance));
    }

    @Test
    void registerCurrentSessionStoresSessionId() {
        CapturingTeamAgent agent = createLeader();
        Session session = AgentSessionApi.create("sess-xyz", Map.of(), agent.getCard());

        agent.registerCurrentSession(session);

        assertEquals("sess-xyz", agent.getSessionManager().getSessionId());
    }

    @Test
    void registerCurrentSessionAlsoRegistersLeaderMemberSession() {
        CapturingTeamAgent agent = createLeader();
        Session session = AgentSessionApi.create("sess-xyz", Map.of(), agent.getCard());

        agent.registerCurrentSession(session);

        assertSame(session, agent.getTeamBackend().getMemberSession("leader-1"));
    }

    private static void assertEventSummary(String eventType, Map<String, Object> payload, String expected) {
        CapturingTeamAgent agent = createStartedLeader();
        try {
            agent.notifyEvent(eventType, payload);

            assertEquals(expected, lastCaptured(agent));
        } finally {
            agent.stopCoordination();
        }
    }

    private static String lastCaptured(CapturingTeamAgent agent) {
        List<String> captured = agent.getCapturedLeaderInputs();
        return captured.get(captured.size() - 1);
    }

    private static CapturingTeamAgent createStartedLeader() {
        CapturingTeamAgent agent = createLeader();
        agent.startCoordination();
        return agent;
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
