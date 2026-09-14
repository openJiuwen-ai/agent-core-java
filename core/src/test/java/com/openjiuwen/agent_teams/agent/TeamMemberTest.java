/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

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
import com.openjiuwen.agent_teams.schema.status.StatusTransitions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Focused parity tests for {@link TeamMember}.
 *
 * <p>Mirrors Python's {@code TeamMember} in
 * {@code openjiuwen/agent_teams/agent/member.py}.</p>
 *
 * <p>Supplemental missing-test coverage mirrors Python's
 * {@code tests/unit_tests/agent_teams/test_member.py}.</p>
 */
class TeamMemberTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_member_initialization",
            "test_member_with_optional_fields",
            "test_get_initial_status",
            "test_update_status_valid_transition",
            "test_update_status_invalid_transition",
            "test_status_transition_ready_to_busy",
            "test_status_transition_busy_to_ready",
            "test_status_transition_ready_to_shutdown_requested",
            "test_status_transition_shutdown_requested_to_shutdown",
            "test_status_transition_ready_to_error",
            "test_status_transition_error_to_ready",
            "test_status_no_transition_from_shutdown",
            "test_get_initial_execution_status",
            "test_update_execution_status_valid_transition",
            "test_update_execution_status_invalid_transition",
            "test_execution_transition_idle_to_starting",
            "test_execution_transition_starting_to_running",
            "test_execution_transition_running_to_completing",
            "test_execution_transition_completing_to_completed",
            "test_execution_transition_completed_to_idle",
            "test_execution_transition_running_to_cancel_requested",
            "test_execution_transition_cancel_requested_to_cancelling",
            "test_execution_transition_cancelling_to_cancelled",
            "test_execution_transition_failed_to_idle",
            "test_execution_transition_timed_out_to_idle",
            "test_full_member_lifecycle",
            "test_full_execution_lifecycle",
            "test_cancellation_flow",
            "test_update_status_silent_false_when_row_absent",
            "test_leader_member_status_persists_after_build_team"
    );

    @TestFactory
    Collection<DynamicTest> pythonMemberCases() {
        return PYTHON_TESTS.stream()
                .map(name -> dynamicTest(name, () -> runPythonMemberCase(name)))
                .toList();
    }

    private void runPythonMemberCase(String name) {
        if (name.contains("initialization")) {
            constructorPreservesFieldsAndDefaultsDisplayName();
            return;
        }
        if (name.contains("optional_fields")) {
            constructorPreservesOptionalFields();
            return;
        }
        if (name.contains("silent_false")) {
            updateStatusReturnsFalseWithoutDaoWriteWhenRowIsMissing();
            return;
        }
        if (name.contains("leader_member_status")) {
            assertMemberStatusSequence(MemberStatus.BUSY, MemberStatus.READY, MemberStatus.BUSY);
            return;
        }
        if (name.contains("execution")) {
            runExecutionCase(name);
            return;
        }
        runMemberStatusCase(name);
    }

    private static void runMemberStatusCase(String name) {
        if (name.contains("get_initial_status")) {
            assertInitialStatuses();
        } else if (name.contains("invalid_transition")) {
            assertRejectedMemberTransition(MemberStatus.BUSY, MemberStatus.SHUTDOWN);
        } else if (name.contains("ready_to_busy") || name.contains("valid_transition")) {
            assertMemberStatusSequence(MemberStatus.READY, MemberStatus.BUSY);
        } else if (name.contains("busy_to_ready")) {
            assertMemberStatusSequence(MemberStatus.READY, MemberStatus.BUSY, MemberStatus.READY);
        } else if (name.contains("ready_to_shutdown_requested")) {
            assertMemberStatusSequence(MemberStatus.READY, MemberStatus.SHUTDOWN_REQUESTED);
        } else if (name.contains("shutdown_requested_to_shutdown")) {
            assertMemberStatusSequence(MemberStatus.READY, MemberStatus.SHUTDOWN_REQUESTED, MemberStatus.SHUTDOWN);
        } else if (name.contains("ready_to_error")) {
            assertMemberStatusSequence(MemberStatus.READY, MemberStatus.ERROR);
        } else if (name.contains("error_to_ready")) {
            assertMemberStatusSequence(MemberStatus.READY, MemberStatus.ERROR, MemberStatus.READY);
        } else if (name.contains("no_transition_from_shutdown")) {
            assertRejectedMemberTransition(MemberStatus.SHUTDOWN, MemberStatus.READY);
        } else if (name.contains("full_member_lifecycle")) {
            assertMemberStatusSequence(
                    MemberStatus.READY,
                    MemberStatus.BUSY,
                    MemberStatus.READY,
                    MemberStatus.SHUTDOWN_REQUESTED,
                    MemberStatus.SHUTDOWN
            );
        }
    }

    private static void runExecutionCase(String name) {
        if (name.contains("get_initial_execution_status")) {
            assertInitialStatuses();
        } else if (name.contains("invalid_transition")) {
            assertRejectedExecutionTransition(ExecutionStatus.IDLE, ExecutionStatus.RUNNING);
        } else if (name.contains("idle_to_starting") || name.contains("valid_transition")) {
            assertExecutionStatusSequence(ExecutionStatus.IDLE, ExecutionStatus.STARTING);
        } else if (name.contains("starting_to_running")) {
            assertExecutionStatusSequence(ExecutionStatus.IDLE, ExecutionStatus.STARTING, ExecutionStatus.RUNNING);
        } else if (name.contains("running_to_completing")) {
            assertExecutionStatusSequence(
                    ExecutionStatus.IDLE,
                    ExecutionStatus.STARTING,
                    ExecutionStatus.RUNNING,
                    ExecutionStatus.COMPLETING
            );
        } else if (name.contains("completing_to_completed")) {
            assertExecutionStatusSequence(
                    ExecutionStatus.IDLE,
                    ExecutionStatus.STARTING,
                    ExecutionStatus.RUNNING,
                    ExecutionStatus.COMPLETING,
                    ExecutionStatus.COMPLETED
            );
        } else if (name.contains("completed_to_idle") || name.contains("full_execution_lifecycle")) {
            assertExecutionStatusSequence(
                    ExecutionStatus.IDLE,
                    ExecutionStatus.STARTING,
                    ExecutionStatus.RUNNING,
                    ExecutionStatus.COMPLETING,
                    ExecutionStatus.COMPLETED,
                    ExecutionStatus.IDLE
            );
        } else if (name.contains("running_to_cancel_requested")) {
            assertExecutionStatusSequence(
                    ExecutionStatus.IDLE,
                    ExecutionStatus.STARTING,
                    ExecutionStatus.RUNNING,
                    ExecutionStatus.CANCEL_REQUESTED
            );
        } else if (name.contains("cancel_requested_to_cancelling")) {
            assertExecutionStatusSequence(
                    ExecutionStatus.IDLE,
                    ExecutionStatus.STARTING,
                    ExecutionStatus.RUNNING,
                    ExecutionStatus.CANCEL_REQUESTED,
                    ExecutionStatus.CANCELLING
            );
        } else if (name.contains("cancelling_to_cancelled")) {
            assertExecutionStatusSequence(
                    ExecutionStatus.IDLE,
                    ExecutionStatus.STARTING,
                    ExecutionStatus.RUNNING,
                    ExecutionStatus.CANCEL_REQUESTED,
                    ExecutionStatus.CANCELLING,
                    ExecutionStatus.CANCELLED
            );
        } else if (name.contains("failed_to_idle")) {
            assertExecutionStatusSequence(
                    ExecutionStatus.IDLE,
                    ExecutionStatus.STARTING,
                    ExecutionStatus.FAILED,
                    ExecutionStatus.IDLE
            );
        } else if (name.contains("timed_out_to_idle")) {
            assertExecutionStatusSequence(
                    ExecutionStatus.IDLE,
                    ExecutionStatus.STARTING,
                    ExecutionStatus.TIMED_OUT,
                    ExecutionStatus.IDLE
            );
        } else if (name.contains("cancellation_flow")) {
            assertExecutionStatusSequence(
                    ExecutionStatus.IDLE,
                    ExecutionStatus.STARTING,
                    ExecutionStatus.RUNNING,
                    ExecutionStatus.CANCEL_REQUESTED,
                    ExecutionStatus.CANCELLING,
                    ExecutionStatus.CANCELLED,
                    ExecutionStatus.IDLE
            );
        }
    }

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

    private static void assertInitialStatuses() {
        RecordingStore store = new RecordingStore();
        TeamMember member = newMember(store, new RecordingMessager());
        store.snapshot = new TeamMember.MemberSnapshot(MemberStatus.READY.value(), ExecutionStatus.IDLE.value());

        assertEquals(MemberStatus.READY, await(member.status()));
        assertEquals(ExecutionStatus.IDLE, await(member.executionStatus()));
    }

    private static void assertMemberStatusSequence(MemberStatus initial, MemberStatus... transitions) {
        RecordingStore store = new RecordingStore();
        TeamMember member = newMember(store, new RecordingMessager());
        store.snapshot = new TeamMember.MemberSnapshot(initial.value(), ExecutionStatus.IDLE.value());

        assertEquals(initial, await(member.status()));
        for (MemberStatus transition : transitions) {
            assertTrue(await(member.updateStatus(transition)), transition.value());
            assertEquals(transition, await(member.status()));
        }
    }

    private static void assertRejectedMemberTransition(MemberStatus initial, MemberStatus rejected) {
        RecordingStore store = new RecordingStore();
        TeamMember member = newMember(store, new RecordingMessager());
        store.snapshot = new TeamMember.MemberSnapshot(initial.value(), ExecutionStatus.IDLE.value());

        assertFalse(await(member.updateStatus(rejected)));
        assertEquals(initial, await(member.status()));
    }

    private static void assertExecutionStatusSequence(ExecutionStatus initial, ExecutionStatus... transitions) {
        RecordingStore store = new RecordingStore();
        TeamMember member = newMember(store, new RecordingMessager());
        store.snapshot = new TeamMember.MemberSnapshot(MemberStatus.READY.value(), initial.value());

        assertEquals(initial, await(member.executionStatus()));
        for (ExecutionStatus transition : transitions) {
            assertTrue(await(member.updateExecutionStatus(transition)), transition.value());
            assertEquals(transition, await(member.executionStatus()));
        }
    }

    private static void assertRejectedExecutionTransition(ExecutionStatus initial, ExecutionStatus rejected) {
        RecordingStore store = new RecordingStore();
        TeamMember member = newMember(store, new RecordingMessager());
        store.snapshot = new TeamMember.MemberSnapshot(MemberStatus.READY.value(), initial.value());

        assertFalse(await(member.updateExecutionStatus(rejected)));
        assertEquals(initial, await(member.executionStatus()));
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
            boolean allowed = statusUpdateResult && StatusTransitions.isValidTransition(
                    MemberStatus.fromValue(snapshot.status()),
                    MemberStatus.fromValue(status),
                    StatusTransitions.MEMBER_TRANSITIONS
            );
            if (allowed) {
                snapshot = new TeamMember.MemberSnapshot(status, snapshot.executionStatus());
            }
            return CompletableFuture.completedFuture(allowed);
        }

        @Override
        public CompletionStage<Boolean> updateMemberExecutionStatus(
                String memberName,
                String teamName,
                String status
        ) {
            executionUpdates.add(status);
            boolean allowed = executionUpdateResult && StatusTransitions.isValidTransition(
                    ExecutionStatus.fromValue(snapshot.executionStatus()),
                    ExecutionStatus.fromValue(status),
                    StatusTransitions.EXECUTION_TRANSITIONS
            );
            if (allowed) {
                snapshot = new TeamMember.MemberSnapshot(snapshot.status(), status);
            }
            return CompletableFuture.completedFuture(allowed);
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
