/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.MemberExecutionChangedEvent;
import com.openjiuwen.agent_teams.schema.MemberStatusChangedEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link TeamMember}.
 *
 * <p>Mirrors Python's {@code TeamMember} in
 * {@code openjiuwen/agent_teams/agent/member.py}.</p>
 */
class TeamMemberTest {

    @Test
    void constructorPreservesFieldsAndDefaultsDisplayName() {
        RecordingStore store = new RecordingStore();
        RecordingMessager messager = new RecordingMessager();
        AgentCard card = new AgentCard("agent", "Agent", "description");

        TeamMember member = new TeamMember("member1", "team1", card, store, messager);

        assertEquals("member1", member.getMemberName());
        assertEquals("team1", member.getTeamName());
        assertEquals("member1", member.getDisplayName());
        assertSame(card, member.getAgentCard());
        assertSame(store, member.getDb());
        assertSame(messager, member.getMessager());
        assertNull(member.getPrompt());
        assertNull(member.getDesc());
    }

    @Test
    void constructorPreservesOptionalFields() {
        TeamMember member = new TeamMember(
                "member2",
                "team1",
                new AgentCard("agent", "Agent", "description"),
                new RecordingStore(),
                new RecordingMessager(),
                "Display",
                "prompt",
                "desc"
        );

        assertEquals("Display", member.getDisplayName());
        assertEquals("prompt", member.getPrompt());
        assertEquals("desc", member.getDesc());
    }

    @Test
    void statusReadsPersistedMemberRowAndMissingRowsAsNull() {
        RecordingStore store = new RecordingStore();
        TeamMember member = newMember(store, new RecordingMessager());
        store.snapshot = new TeamMember.MemberSnapshot(MemberStatus.READY.value(), ExecutionStatus.IDLE.value());

        assertEquals(MemberStatus.READY, await(member.status()));
        assertEquals(ExecutionStatus.IDLE, await(member.executionStatus()));

        store.snapshot = null;
        assertNull(await(member.status()));
        assertNull(await(member.executionStatus()));
    }

    @Test
    void updateStatusReturnsFalseWithoutDaoWriteWhenRowIsMissing() {
        RecordingStore store = new RecordingStore();
        TeamMember member = newMember(store, new RecordingMessager());

        assertFalse(await(member.updateStatus(MemberStatus.READY)));
        assertEquals(0, store.statusUpdates.size());
    }

    @Test
    void updateStatusShortCircuitsSameStatusWithoutPublish() {
        RecordingStore store = new RecordingStore();
        RecordingMessager messager = new RecordingMessager();
        TeamMember member = newMember(store, messager);
        store.snapshot = new TeamMember.MemberSnapshot(MemberStatus.READY.value(), ExecutionStatus.IDLE.value());

        assertTrue(await(member.updateStatus(MemberStatus.READY)));
        assertEquals(0, store.statusUpdates.size());
        assertEquals(0, messager.messages.size());
    }

    @Test
    void updateStatusPersistsAndPublishesStatusChangedEvent() {
        RecordingStore store = new RecordingStore();
        RecordingMessager messager = new RecordingMessager();
        TeamMember member = newMember(store, messager);
        store.snapshot = new TeamMember.MemberSnapshot(MemberStatus.READY.value(), ExecutionStatus.IDLE.value());

        AgentTeamsContext.SessionIdToken token = AgentTeamsContext.setSessionId("session-1");
        try {
            assertTrue(await(member.updateStatus(MemberStatus.BUSY)));
        } finally {
            AgentTeamsContext.resetSessionId(token);
        }

        assertEquals(List.of(MemberStatus.BUSY.value()), store.statusUpdates);
        assertEquals(MemberStatus.BUSY.value(), store.snapshot.status());
        assertEquals(List.of("session:session-1:team:team1:team"), messager.topicIds);
        assertEquals(1, messager.messages.size());
        EventMessage message = messager.messages.get(0);
        assertEquals(TeamEvent.MEMBER_STATUS_CHANGED, message.getEventType());
        MemberStatusChangedEvent payload = (MemberStatusChangedEvent) message.getPayload();
        assertEquals("team1", payload.getTeamName());
        assertEquals("member1", payload.getMemberName());
        assertEquals(MemberStatus.READY.value(), payload.getOldStatus());
        assertEquals(MemberStatus.BUSY.value(), payload.getNewStatus());
    }

    @Test
    void updateStatusReturnsFalseWhenStoreRejectsTransition() {
        RecordingStore store = new RecordingStore();
        RecordingMessager messager = new RecordingMessager();
        TeamMember member = newMember(store, messager);
        store.snapshot = new TeamMember.MemberSnapshot(MemberStatus.BUSY.value(), ExecutionStatus.IDLE.value());
        store.statusUpdateResult = false;

        assertFalse(await(member.updateStatus(MemberStatus.SHUTDOWN)));
        assertEquals(List.of(MemberStatus.SHUTDOWN.value()), store.statusUpdates);
        assertEquals(MemberStatus.BUSY.value(), store.snapshot.status());
        assertEquals(0, messager.messages.size());
    }

    @Test
    void updateExecutionStatusPersistsAndPublishesExecutionChangedEvent() {
        RecordingStore store = new RecordingStore();
        RecordingMessager messager = new RecordingMessager();
        TeamMember member = newMember(store, messager);
        store.snapshot = new TeamMember.MemberSnapshot(MemberStatus.READY.value(), ExecutionStatus.IDLE.value());

        AgentTeamsContext.SessionIdToken token = AgentTeamsContext.setSessionId("session-2");
        try {
            assertTrue(await(member.updateExecutionStatus(ExecutionStatus.STARTING)));
        } finally {
            AgentTeamsContext.resetSessionId(token);
        }

        assertEquals(List.of(ExecutionStatus.STARTING.value()), store.executionUpdates);
        assertEquals(ExecutionStatus.STARTING.value(), store.snapshot.executionStatus());
        assertEquals(List.of("session:session-2:team:team1:team"), messager.topicIds);
        EventMessage message = messager.messages.get(0);
        assertEquals(TeamEvent.MEMBER_EXECUTION_CHANGED, message.getEventType());
        MemberExecutionChangedEvent payload = (MemberExecutionChangedEvent) message.getPayload();
        assertEquals("team1", payload.getTeamName());
        assertEquals("member1", payload.getMemberName());
        assertEquals(ExecutionStatus.IDLE.value(), payload.getOldStatus());
        assertEquals(ExecutionStatus.STARTING.value(), payload.getNewStatus());
    }

    @Test
    void publishFailureIsLoggedButSuccessfulDbUpdateStillReturnsTrue() {
        RecordingStore store = new RecordingStore();
        RecordingMessager messager = new RecordingMessager();
        TeamMember member = newMember(store, messager);
        store.snapshot = new TeamMember.MemberSnapshot(MemberStatus.READY.value(), ExecutionStatus.IDLE.value());
        messager.failPublish = true;

        assertTrue(await(member.updateStatus(MemberStatus.BUSY)));
        assertEquals(MemberStatus.BUSY.value(), store.snapshot.status());
    }

    private static TeamMember newMember(RecordingStore store, RecordingMessager messager) {
        return new TeamMember(
                "member1",
                "team1",
                new AgentCard("agent", "Agent", "description"),
                store,
                messager,
                "Member One",
                null,
                null
        );
    }

    private static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static final class RecordingStore implements TeamMember.MemberStore {
        private TeamMember.MemberSnapshot snapshot;
        private boolean statusUpdateResult = true;
        private boolean executionUpdateResult = true;
        private final List<String> statusUpdates = new ArrayList<>();
        private final List<String> executionUpdates = new ArrayList<>();

        @Override
        public CompletionStage<TeamMember.MemberSnapshot> getMember(String memberName, String teamName) {
            return CompletableFuture.completedFuture(snapshot);
        }

        @Override
        public CompletionStage<Boolean> updateMemberStatus(String memberName, String teamName, String status) {
            statusUpdates.add(status);
            if (statusUpdateResult) {
                snapshot = new TeamMember.MemberSnapshot(status, snapshot.executionStatus());
            }
            return CompletableFuture.completedFuture(statusUpdateResult);
        }

        @Override
        public CompletionStage<Boolean> updateMemberExecutionStatus(
                String memberName,
                String teamName,
                String status
        ) {
            executionUpdates.add(status);
            if (executionUpdateResult) {
                snapshot = new TeamMember.MemberSnapshot(snapshot.status(), status);
            }
            return CompletableFuture.completedFuture(executionUpdateResult);
        }
    }

    private static final class RecordingMessager implements Messager {
        private final List<String> topicIds = new ArrayList<>();
        private final List<EventMessage> messages = new ArrayList<>();
        private boolean failPublish;

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
            if (failPublish) {
                return CompletableFuture.failedFuture(new IllegalStateException("publish failed"));
            }
            topicIds.add(topicId);
            messages.add(message);
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
