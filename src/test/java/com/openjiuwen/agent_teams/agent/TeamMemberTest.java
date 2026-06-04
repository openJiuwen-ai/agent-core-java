package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.spawn.SpawnContext;
import com.openjiuwen.agent_teams.tools.TeamDatabase;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_member.py}.
 */
class TeamMemberTest {

    @AfterEach
    void resetSessionContext() {
        SpawnContext.resetSessionId();
    }

    @Test
    void memberInitialization() {
        Fixture fixture = fixture();

        assertEquals("member1", fixture.member.getMemberName());
        assertEquals("test_team", fixture.member.getTeamName());
        assertEquals("Test Member", fixture.member.getDisplayName());
        assertSame(fixture.agentCard, fixture.member.getAgentCard());
        assertEquals(MemberStatus.READY, fixture.member.status());
        assertEquals(ExecutionStatus.IDLE, fixture.member.executionStatus());
    }

    @Test
    void memberWithOptionalFields() {
        Fixture fixture = fixture("member2", "Test Member with Options", "You are a helpful assistant", "A helpful team member");

        assertEquals("You are a helpful assistant", fixture.member.getPrompt());
        assertEquals("A helpful team member", fixture.member.getDesc());
    }

    @Test
    void getInitialStatus() {
        assertEquals(MemberStatus.READY, fixture().member.status());
    }

    @Test
    void updateStatusValidTransition() {
        Fixture fixture = fixture();

        assertTrue(fixture.member.updateStatus(MemberStatus.BUSY));
        assertEquals(MemberStatus.BUSY, fixture.member.status());
        assertEquals(1, fixture.messager.events.size());
    }

    @Test
    void updateStatusInvalidTransition() {
        Fixture fixture = fixture();

        assertTrue(fixture.member.updateStatus(MemberStatus.BUSY));
        assertFalse(fixture.member.updateStatus(MemberStatus.SHUTDOWN));
        assertEquals(MemberStatus.BUSY, fixture.member.status());
    }

    @Test
    void statusTransitionReadyToBusy() {
        Fixture fixture = fixture();

        assertEquals(MemberStatus.READY, fixture.member.status());
        assertTrue(fixture.member.updateStatus(MemberStatus.BUSY));
        assertEquals(MemberStatus.BUSY, fixture.member.status());
    }

    @Test
    void statusTransitionBusyToReady() {
        Fixture fixture = fixture();

        fixture.member.updateStatus(MemberStatus.BUSY);
        assertTrue(fixture.member.updateStatus(MemberStatus.READY));
        assertEquals(MemberStatus.READY, fixture.member.status());
    }

    @Test
    void statusTransitionReadyToShutdownRequested() {
        Fixture fixture = fixture();

        assertTrue(fixture.member.updateStatus(MemberStatus.SHUTDOWN_REQUESTED));
        assertEquals(MemberStatus.SHUTDOWN_REQUESTED, fixture.member.status());
    }

    @Test
    void statusTransitionShutdownRequestedToShutdown() {
        Fixture fixture = fixture();

        fixture.member.updateStatus(MemberStatus.SHUTDOWN_REQUESTED);
        assertTrue(fixture.member.updateStatus(MemberStatus.SHUTDOWN));
        assertEquals(MemberStatus.SHUTDOWN, fixture.member.status());
    }

    @Test
    void statusTransitionReadyToError() {
        Fixture fixture = fixture();

        assertTrue(fixture.member.updateStatus(MemberStatus.ERROR));
        assertEquals(MemberStatus.ERROR, fixture.member.status());
    }

    @Test
    void statusTransitionErrorToReady() {
        Fixture fixture = fixture();

        fixture.member.updateStatus(MemberStatus.ERROR);
        assertTrue(fixture.member.updateStatus(MemberStatus.READY));
        assertEquals(MemberStatus.READY, fixture.member.status());
    }

    @Test
    void statusNoTransitionFromShutdown() {
        Fixture fixture = fixture();

        fixture.member.updateStatus(MemberStatus.BUSY);
        fixture.member.updateStatus(MemberStatus.SHUTDOWN_REQUESTED);
        fixture.member.updateStatus(MemberStatus.SHUTDOWN);

        assertFalse(fixture.member.updateStatus(MemberStatus.READY));
        assertEquals(MemberStatus.SHUTDOWN, fixture.member.status());
    }

    @Test
    void getInitialExecutionStatus() {
        assertEquals(ExecutionStatus.IDLE, fixture().member.executionStatus());
    }

    @Test
    void updateExecutionStatusValidTransition() {
        Fixture fixture = fixture();

        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.STARTING));
        assertEquals(ExecutionStatus.STARTING, fixture.member.executionStatus());
    }

    @Test
    void updateExecutionStatusInvalidTransition() {
        Fixture fixture = fixture();

        assertFalse(fixture.member.updateExecutionStatus(ExecutionStatus.RUNNING));
        assertEquals(ExecutionStatus.IDLE, fixture.member.executionStatus());
    }

    @Test
    void executionTransitionIdleToStarting() {
        Fixture fixture = fixture();

        assertEquals(ExecutionStatus.IDLE, fixture.member.executionStatus());
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.STARTING));
        assertEquals(ExecutionStatus.STARTING, fixture.member.executionStatus());
    }

    @Test
    void executionTransitionStartingToRunning() {
        Fixture fixture = fixture();

        fixture.member.updateExecutionStatus(ExecutionStatus.STARTING);
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.RUNNING));
        assertEquals(ExecutionStatus.RUNNING, fixture.member.executionStatus());
    }

    @Test
    void executionTransitionRunningToCompleting() {
        Fixture fixture = runningFixture();

        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.COMPLETING));
        assertEquals(ExecutionStatus.COMPLETING, fixture.member.executionStatus());
    }

    @Test
    void executionTransitionCompletingToCompleted() {
        Fixture fixture = runningFixture();

        fixture.member.updateExecutionStatus(ExecutionStatus.COMPLETING);
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.COMPLETED));
        assertEquals(ExecutionStatus.COMPLETED, fixture.member.executionStatus());
    }

    @Test
    void executionTransitionCompletedToIdle() {
        Fixture fixture = runningFixture();

        fixture.member.updateExecutionStatus(ExecutionStatus.COMPLETING);
        fixture.member.updateExecutionStatus(ExecutionStatus.COMPLETED);
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.IDLE));
        assertEquals(ExecutionStatus.IDLE, fixture.member.executionStatus());
    }

    @Test
    void executionTransitionRunningToCancelRequested() {
        Fixture fixture = runningFixture();

        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.CANCEL_REQUESTED));
        assertEquals(ExecutionStatus.CANCEL_REQUESTED, fixture.member.executionStatus());
    }

    @Test
    void executionTransitionCancelRequestedToCancelling() {
        Fixture fixture = runningFixture();

        fixture.member.updateExecutionStatus(ExecutionStatus.CANCEL_REQUESTED);
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.CANCELLING));
        assertEquals(ExecutionStatus.CANCELLING, fixture.member.executionStatus());
    }

    @Test
    void executionTransitionCancellingToCancelled() {
        Fixture fixture = runningFixture();

        fixture.member.updateExecutionStatus(ExecutionStatus.CANCEL_REQUESTED);
        fixture.member.updateExecutionStatus(ExecutionStatus.CANCELLING);
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.CANCELLED));
        assertEquals(ExecutionStatus.CANCELLED, fixture.member.executionStatus());
    }

    @Test
    void executionTransitionFailedToIdle() {
        Fixture fixture = fixture();

        fixture.member.updateExecutionStatus(ExecutionStatus.STARTING);
        fixture.member.updateExecutionStatus(ExecutionStatus.FAILED);
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.IDLE));
        assertEquals(ExecutionStatus.IDLE, fixture.member.executionStatus());
    }

    @Test
    void executionTransitionTimedOutToIdle() {
        Fixture fixture = fixture();

        fixture.member.updateExecutionStatus(ExecutionStatus.STARTING);
        fixture.member.updateExecutionStatus(ExecutionStatus.TIMED_OUT);
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.IDLE));
        assertEquals(ExecutionStatus.IDLE, fixture.member.executionStatus());
    }

    @Test
    void fullMemberLifecycle() {
        Fixture fixture = fixture();

        assertEquals(MemberStatus.READY, fixture.member.status());
        assertTrue(fixture.member.updateStatus(MemberStatus.BUSY));
        assertTrue(fixture.member.updateStatus(MemberStatus.READY));
        assertTrue(fixture.member.updateStatus(MemberStatus.SHUTDOWN_REQUESTED));
        assertTrue(fixture.member.updateStatus(MemberStatus.SHUTDOWN));
        assertEquals(MemberStatus.SHUTDOWN, fixture.member.status());
    }

    @Test
    void fullExecutionLifecycle() {
        Fixture fixture = fixture();

        assertEquals(ExecutionStatus.IDLE, fixture.member.executionStatus());
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.STARTING));
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.RUNNING));
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.COMPLETING));
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.COMPLETED));
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.IDLE));
        assertEquals(ExecutionStatus.IDLE, fixture.member.executionStatus());
    }

    @Test
    void cancellationFlow() {
        Fixture fixture = fixture();

        assertEquals(ExecutionStatus.IDLE, fixture.member.executionStatus());
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.STARTING));
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.RUNNING));
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.CANCEL_REQUESTED));
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.CANCELLING));
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.CANCELLED));
        assertTrue(fixture.member.updateExecutionStatus(ExecutionStatus.IDLE));
        assertEquals(ExecutionStatus.IDLE, fixture.member.executionStatus());
    }

    private static Fixture runningFixture() {
        Fixture fixture = fixture();
        fixture.member.updateExecutionStatus(ExecutionStatus.STARTING);
        fixture.member.updateExecutionStatus(ExecutionStatus.RUNNING);
        return fixture;
    }

    private static Fixture fixture() {
        return fixture("member1", "Test Member", null, null);
    }

    private static Fixture fixture(String memberName, String displayName, String prompt, String desc) {
        SpawnContext.setSessionId("team-member-session-" + memberName);
        TeamDatabase db = new TeamDatabase(DatabaseConfig.inMemory());
        db.initialize();
        db.getTeamDao().createTeam("test_team", "Test Team", "leader1", null, null).join();
        AgentCard agentCard = AgentCard.builder()
                .name("TestAgent")
                .description("Test agent for unit tests")
                .version("1.0.0")
                .build();
        db.getMemberDao().createMember(
                memberName,
                "test_team",
                displayName,
                "{}",
                MemberStatus.READY.value(),
                desc,
                ExecutionStatus.IDLE.value(),
                "build_mode",
                prompt,
                null
        ).join();
        RecordingMessager messager = new RecordingMessager();
        TeamMember member = new TeamMember(
                memberName,
                "test_team",
                agentCard,
                db,
                messager,
                displayName,
                prompt,
                desc
        );
        return new Fixture(member, db, agentCard, messager);
    }

    private record Fixture(TeamMember member, TeamDatabase db, AgentCard agentCard, RecordingMessager messager) {
    }

    private static final class RecordingMessager implements Messager {
        private final List<EventMessage> events = new ArrayList<>();

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void publish(String topicId, EventMessage message) {
            events.add(message);
        }

        @Override
        public void subscribe(String topicId, MessagerHandler handler) {
        }

        @Override
        public void unsubscribe(String topicId) {
        }

        @Override
        public void send(String agentId, EventMessage message) {
        }

        @Override
        public void registerDirectMessageHandler(MessagerHandler handler) {
        }

        @Override
        public void unregisterDirectMessageHandler() {
        }
    }
}
