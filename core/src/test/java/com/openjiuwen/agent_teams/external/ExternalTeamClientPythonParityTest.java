/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.messager.InProcessMessager;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.schema.TaskDetail;
import com.openjiuwen.agent_teams.schema.TaskOpResult;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.InMemoryTeamDatabase;
import com.openjiuwen.agent_teams.tools.TeamMember;
import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's external team client tests in
 * {@code tests/unit_tests/agent_teams/external/test_client.py}.
 */
class ExternalTeamClientPythonParityTest {

    @AfterEach
    void tearDown() {
        InProcessMessager.cleanupInprocessBus();
        AgentTeamsContext.resetSessionId(new AgentTeamsContext.SessionIdToken(null, false));
    }

    @Test
    void testSendMessageIsReceivedByTarget() {
        InMemoryTeamDatabase db = database();
        try (ExternalTeamClient dev = connectedClient(db, "dev-1", "teammate")) {
            String messageId = dev.sendMessage("leader", "hello leader").toCompletableFuture().join();
            assertThat(messageId).isNotBlank();
        }

        try (ExternalTeamClient leader = connectedClient(db, "leader", "leader")) {
            InboxView inbox = leader.fetchInbox().toCompletableFuture().join();
            assertThat(inbox.messages()).anyMatch(message -> "hello leader".equals(message.getContent()));
        }
    }

    @Test
    void testBroadcastIsReceived() {
        InMemoryTeamDatabase db = database();
        try (ExternalTeamClient leader = connectedClient(db, "leader", "leader")) {
            String messageId = leader.sendMessage("*", "team announcement").toCompletableFuture().join();
            assertThat(messageId).isNotBlank();
        }

        try (ExternalTeamClient dev = connectedClient(db, "dev-1", "teammate")) {
            InboxView inbox = dev.fetchInbox().toCompletableFuture().join();
            assertThat(inbox.messages()).anyMatch(message -> "team announcement".equals(message.getContent()));
        }
    }

    @Test
    void testFetchInboxMarksMessagesRead() {
        InMemoryTeamDatabase db = database();
        try (ExternalTeamClient leader = connectedClient(db, "leader", "leader")) {
            leader.sendMessage("dev-1", "your task").toCompletableFuture().join();
        }

        try (ExternalTeamClient dev = connectedClient(db, "dev-1", "teammate")) {
            InboxView first = dev.fetchInbox().toCompletableFuture().join();
            assertThat(first.messages()).anyMatch(message -> "your task".equals(message.getContent()));

            InboxView second = dev.fetchInbox().toCompletableFuture().join();
            assertThat(second.messages()).noneMatch(message -> "your task".equals(message.getContent()));
        }
    }

    @Test
    void testClaimAndCompleteTask() {
        InMemoryTeamDatabase db = database();
        db.createTask("t1", "ext_team", "Do X", "details", TaskStatus.PENDING.value()).join();

        try (ExternalTeamClient dev = connectedClient(db, "dev-1", "teammate")) {
            assertThat(dev.claimableTasks().toCompletableFuture().join())
                    .anyMatch(task -> "t1".equals(task.getTaskId()));

            TaskOpResult claim = dev.claimTask("t1").toCompletableFuture().join();
            assertThat(claim.ok()).as(claim.reason()).isTrue();
            TaskOpResult complete = dev.completeTask("t1").toCompletableFuture().join();
            assertThat(complete.ok()).as(complete.reason()).isTrue();

            TaskDetail detail = dev.getTask("t1").toCompletableFuture().join();
            assertThat(detail).isNotNull();
            assertThat(detail.getStatus()).isEqualTo(TaskStatus.COMPLETED.value());
        }
    }

    @Test
    void testUpdateTaskEditsContent() {
        InMemoryTeamDatabase db = database();
        db.createTask("t2", "ext_team", "Old", "old", TaskStatus.PENDING.value()).join();

        try (ExternalTeamClient leader = connectedClient(db, "leader", "leader")) {
            TaskOpResult result = leader.updateTask("t2", "New", "new").toCompletableFuture().join();
            assertThat(result.ok()).as(result.reason()).isTrue();

            TaskDetail detail = leader.getTask("t2").toCompletableFuture().join();
            assertThat(detail).isNotNull();
            assertThat(detail.getTitle()).isEqualTo("New");
            assertThat(detail.getContent()).isEqualTo("new");
        }
    }

    @Test
    void testListMembersReturnsRoster() {
        InMemoryTeamDatabase db = database();
        try (ExternalTeamClient dev = connectedClient(db, "dev-1", "teammate")) {
            List<String> names = dev.listMembers().toCompletableFuture().join()
                    .stream()
                    .map(TeamMember::getMemberName)
                    .toList();

            assertThat(names).contains("leader", "dev-1");
        }
    }

    @Test
    void testOperationsBeforeConnectRaise() {
        ExternalTeamClient client = new ExternalTeamClient(descriptor("dev-1", "teammate"), database(), messager("dev-1"));

        assertThatThrownBy(client::listMembers).isInstanceOf(BaseError.class);
    }

    @Test
    void testWatchWakesOnInboundMessage() throws Exception {
        InMemoryTeamDatabase db = database();
        List<String> received = new ArrayList<>();
        CompletableFuture<Void> ready = new CompletableFuture<>();

        try (ExternalTeamClient dev = connectedClient(db, "dev-1", "teammate")) {
            CompletableFuture<Void> watch = dev.watch(view -> {
                view.messages().forEach(message -> received.add(message.getContent()));
                ready.complete(null);
                return CompletableFuture.completedFuture(null);
            }).toCompletableFuture();

            try (ExternalTeamClient leader = connectedClient(db, "leader", "leader")) {
                leader.sendMessage("dev-1", "wake up").toCompletableFuture().join();
            }

            ready.get(2, TimeUnit.SECONDS);
            watch.cancel(true);
        }

        assertThat(received).contains("wake up");
    }

    private static ExternalTeamClient connectedClient(InMemoryTeamDatabase db, String memberName, String role) {
        ExternalTeamClient client = new ExternalTeamClient(descriptor(memberName, role), db, messager(memberName));
        client.connect().toCompletableFuture().join();
        return client;
    }

    private static TeamJoinDescriptor descriptor(String memberName, String role) {
        TeamJoinDescriptor descriptor = new TeamJoinDescriptor();
        descriptor.setSessionId("ext_session");
        descriptor.setTeamName("ext_team");
        descriptor.setMemberName(memberName);
        descriptor.setRole(role);
        descriptor.setLanguage("en");
        descriptor.setDbConfig(Map.of("db_type", "memory"));
        descriptor.setTransportConfig(transport(memberName));
        return descriptor;
    }

    private static InMemoryTeamDatabase database() {
        InMemoryTeamDatabase db = new InMemoryTeamDatabase();
        db.createTeam("ext_team", "External Team", "leader", "desc", "prompt").join();
        db.createMember("leader", "ext_team", "Leader", "leader persona", "active", "leader", null, null, null, null, null)
                .join();
        db.createMember("dev-1", "ext_team", "Dev 1", "dev persona", "active").join();
        return db;
    }

    private static InProcessMessager messager(String nodeId) {
        return new InProcessMessager(transport(nodeId));
    }

    private static MessagerTransportConfig transport(String nodeId) {
        MessagerTransportConfig config = new MessagerTransportConfig();
        config.setNodeId(nodeId);
        return config;
    }
}
