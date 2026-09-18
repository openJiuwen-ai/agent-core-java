/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.agent.coordination.EventBus;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.BaseEventMessage;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.TeamStandbyEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.status.StatusTransitions;
import com.openjiuwen.agent_teams.tools.InMemoryTeamDatabase;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Supplemental persistent-team parity tests.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_teams/test_persistent_team.py}.</p>
 */
class PersistentTeamTest {

    @AfterEach
    void resetSessionContext() {
        AgentTeamsContext.resetSessionId(null);
    }

    @Test
    void pausePollsStopsPolling() {
        EventBus loop = new EventBus(AgentConfigurator.TeamRole.LEADER, 30.0, 30.0);
        try {
            loop.start().toCompletableFuture().join();
            assertThat(loop.isRunning()).isTrue();
            assertThat(loop.isPollsPaused()).isFalse();

            loop.pausePolls().toCompletableFuture().join();

            assertThat(loop.isPollsPaused()).isTrue();
            assertThat(loop.hasMailboxPollTask()).isFalse();
            assertThat(loop.hasTaskPollTask()).isFalse();
            assertThat(loop.isRunning()).isTrue();
        } finally {
            loop.close();
        }
    }

    @Test
    void resumePollsRestartsPolling() {
        EventBus loop = new EventBus(AgentConfigurator.TeamRole.LEADER, 30.0, 30.0);
        try {
            loop.start().toCompletableFuture().join();
            loop.pausePolls().toCompletableFuture().join();
            assertThat(loop.isPollsPaused()).isTrue();

            loop.resumePolls().toCompletableFuture().join();

            assertThat(loop.isPollsPaused()).isFalse();
            assertThat(loop.hasMailboxPollTask()).isTrue();
            assertThat(loop.hasTaskPollTask()).isTrue();
        } finally {
            loop.close();
        }
    }

    @Test
    void pausePollsIsIdempotent() {
        EventBus loop = new EventBus(AgentConfigurator.TeamRole.LEADER, 30.0, 30.0);
        try {
            loop.start().toCompletableFuture().join();
            loop.pausePolls().toCompletableFuture().join();
            loop.pausePolls().toCompletableFuture().join();

            assertThat(loop.isPollsPaused()).isTrue();
        } finally {
            loop.close();
        }
    }

    @Test
    void resumePollsNoopWhenNotPaused() {
        EventBus loop = new EventBus(AgentConfigurator.TeamRole.LEADER, 30.0, 30.0);
        try {
            loop.start().toCompletableFuture().join();

            loop.resumePolls().toCompletableFuture().join();

            assertThat(loop.isPollsPaused()).isFalse();
            assertThat(loop.hasMailboxPollTask()).isTrue();
            assertThat(loop.hasTaskPollTask()).isTrue();
        } finally {
            loop.close();
        }
    }

    @Test
    void standbyEventSerializationUsesTeamEventType() {
        TeamStandbyEvent event = standbyEvent();

        EventMessage message = EventMessage.fromEvent(event);

        assertThat(message.getEventType()).isEqualTo(TeamEvent.STANDBY);
    }

    @Test
    void standbyEventDeserializationRestoresPayload() {
        TeamStandbyEvent event = standbyEvent();
        EventMessage message = EventMessage.fromEvent(event);

        BaseEventMessage payload = message.getPayload();

        assertThat(payload).isInstanceOf(TeamStandbyEvent.class);
        assertThat(payload.getTeamName()).isEqualTo("test_team");
    }

    @Test
    void readyToReadyIsValid() {
        assertThat(StatusTransitions.isValidTransition(
                MemberStatus.READY,
                MemberStatus.READY,
                StatusTransitions.MEMBER_TRANSITIONS)).isTrue();
    }

    @Test
    void readyToBusyStillValid() {
        assertThat(StatusTransitions.isValidTransition(
                MemberStatus.READY,
                MemberStatus.BUSY,
                StatusTransitions.MEMBER_TRANSITIONS)).isTrue();
    }

    @Test
    void buildTeamPersistentMembersRemainUnstarted() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        TeamBackend persistentTeam = persistentTeam(database);

        persistentTeam.buildTeam("Persistent Team", "A persistent team", "Leader", "PM")
                .toCompletableFuture()
                .join();

        assertThat(database.getMember("dev-1", "persistent_team").join()).get()
                .extracting(member -> member.getStatus())
                .isEqualTo(MemberStatus.UNSTARTED.value());
    }

    @Test
    void persistentTeamMemberCanGoReadyThenReady() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        TeamBackend persistentTeam = persistentTeam(database);
        persistentTeam.buildTeam("Persistent Team", "desc", "Leader", "PM")
                .toCompletableFuture()
                .join();

        database.updateMemberStatus("dev-1", "persistent_team", MemberStatus.READY.value()).join();
        assertThat(database.getMember("dev-1", "persistent_team").join()).get()
                .extracting(member -> member.getStatus())
                .isEqualTo(MemberStatus.READY.value());

        boolean success = database.updateMemberStatus("dev-1", "persistent_team", MemberStatus.READY.value()).join();

        assertThat(success).isTrue();
        assertThat(database.getMember("dev-1", "persistent_team").join()).get()
                .extracting(member -> member.getStatus())
                .isEqualTo(MemberStatus.READY.value());
    }

    @Test
    void newSessionCreatesDynamicTablesAndKeepsStaticTeam() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        AgentTeamsContext.setSessionId("session_1");
        database.initialize().join();
        database.createTeam("persistent_team", "PT", "leader1", "", null).join();

        AgentTeamsContext.SessionIdToken token = AgentTeamsContext.setSessionId("session_2");
        try {
            database.createCurSessionTables().join();

            assertThat(database.getTeam("persistent_team").join()).get()
                    .extracting(team -> team.getDisplayName())
                    .isEqualTo("PT");
        } finally {
            AgentTeamsContext.resetSessionId(token);
        }
    }

    private static TeamStandbyEvent standbyEvent() {
        TeamStandbyEvent event = new TeamStandbyEvent();
        event.setTeamName("test_team");
        return event;
    }

    private static TeamBackend persistentTeam(InMemoryTeamDatabase database) {
        AgentTeamsContext.setSessionId("session_1");
        TeamMemberSpec predefined = new TeamMemberSpec(
                "dev-1",
                "Developer",
                TeamRole.TEAMMATE,
                "Backend dev");
        return new TeamBackend(
                "persistent_team",
                "leader1",
                true,
                database,
                new NoopMessager(),
                MemberMode.BUILD_MODE,
                List.of(predefined),
                null,
                null,
                false,
                false,
                List.of(),
                null,
                null,
                null,
                null,
                null);
    }

    private static final class NoopMessager implements Messager {
        @Override
        public CompletionStage<Void> start() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> publish(String topicId, EventMessage message) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> subscribe(String topicId, MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unsubscribe(String topicId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> send(String agentId, EventMessage message) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> registerDirectMessageHandler(MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterDirectMessageHandler() {
            return CompletableFuture.completedFuture(null);
        }
    }
}
