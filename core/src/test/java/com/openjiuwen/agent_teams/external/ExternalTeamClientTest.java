/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.messager.InProcessMessager;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.schema.TaskOpResult;
import com.openjiuwen.agent_teams.tools.InMemoryTeamDatabase;
import com.openjiuwen.agent_teams.tools.TeamMessageManager;
import com.openjiuwen.core.common.exception.BaseError;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code ExternalTeamClient} and {@code InboxView} in
 * {@code openjiuwen/agent_teams/external/client.py}.
 */
class ExternalTeamClientTest {

    @AfterEach
    void tearDown() {
        InProcessMessager.cleanupInprocessBus();
        AgentTeamsContext.resetSessionId(new AgentTeamsContext.SessionIdToken(null, false));
    }

    @Test
    void operationsBeforeConnectRaiseStateInvalid() {
        ExternalTeamClient client = new ExternalTeamClient(descriptor("dev"), database(), messager("client"));

        assertThrows(BaseError.class, () -> client.listTasks());
    }

    @Test
    void connectExposesDescriptorPropertiesAndIsIdempotent() {
        ExternalTeamClient client = new ExternalTeamClient(descriptor("leader", "leader"), database(), messager("leader"));

        client.connect().toCompletableFuture().join();
        client.connect().toCompletableFuture().join();

        assertThat(client.getSessionId()).isEqualTo("session-1");
        assertThat(client.getTeamName()).isEqualTo("team-a");
        assertThat(client.getMemberName()).isEqualTo("leader");
        assertThat(client.getLanguage()).isEqualTo("en");
        assertThat(client.isLeader()).isTrue();
        assertThat(AgentTeamsContext.getSessionId()).isEqualTo("session-1");

        client.close();

        assertThat(AgentTeamsContext.getSessionId()).isEmpty();
    }

    @Test
    void messagingTasksMembersAndInboxUseManagers() {
        InMemoryTeamDatabase db = database();
        InProcessMessager bus = messager("client");
        ExternalTeamClient client = new ExternalTeamClient(descriptor("dev"), db, bus);
        client.connect().toCompletableFuture().join();

        TeamMessageManager peerMessages = new TeamMessageManager("team-a", "peer", db, bus);
        peerMessages.sendMessage("direct hello", "dev").toCompletableFuture().join();
        peerMessages.broadcastMessage("broadcast hello").toCompletableFuture().join();
        db.createTask("task-1", "team-a", "Title", "Body", "pending").join();

        InboxView inbox = client.fetchInbox(true).toCompletableFuture().join();
        TaskOpResult update = client.updateTask("task-1", "Updated", "New body").toCompletableFuture().join();
        TaskOpResult claim = client.claimTask("task-1").toCompletableFuture().join();
        TaskOpResult complete = client.completeTask("task-1").toCompletableFuture().join();

        assertThat(inbox.isEmpty()).isFalse();
        assertThat(inbox.messages()).hasSize(2);
        assertThat(inbox.tasks()).extracting("taskId").containsExactly("task-1");
        assertThat(client.fetchInbox(true).toCompletableFuture().join().messages()).isEmpty();
        assertThat(update.ok()).isTrue();
        assertThat(claim.ok()).isTrue();
        assertThat(complete.ok()).isTrue();
        assertThat(client.listMembers().toCompletableFuture().join()).hasSize(3);
        assertThat(client.listTasks().toCompletableFuture().join().get(0).getStatus()).isEqualTo("completed");
        client.close();
    }

    @Test
    void watchSubscribesToMessageAndTaskTopicsUntilCancelled() throws Exception {
        InMemoryTeamDatabase db = database();
        InProcessMessager bus = messager("client");
        ExternalTeamClient client = new ExternalTeamClient(descriptor("dev"), db, bus);
        client.connect().toCompletableFuture().join();
        TeamMessageManager peerMessages = new TeamMessageManager("team-a", "peer", db, bus);
        CompletableFuture<Void> observed = new CompletableFuture<>();
        AtomicReference<InboxView> observedView = new AtomicReference<>();

        CompletableFuture<Void> watch = client.watch(view -> {
            observedView.set(view);
            observed.complete(null);
            return CompletableFuture.completedFuture(null);
        }).toCompletableFuture();
        peerMessages.sendMessage("wake", "dev").toCompletableFuture().join();

        observed.get(2, TimeUnit.SECONDS);
        watch.cancel(true);

        assertThat(observedView.get()).isNotNull();
        assertThat(observedView.get().messages()).hasSize(1);
        client.close();
    }

    private static TeamJoinDescriptor descriptor(String memberName) {
        return descriptor(memberName, "teammate");
    }

    private static TeamJoinDescriptor descriptor(String memberName, String role) {
        TeamJoinDescriptor descriptor = new TeamJoinDescriptor();
        descriptor.setSessionId("session-1");
        descriptor.setTeamName("team-a");
        descriptor.setMemberName(memberName);
        descriptor.setRole(role);
        descriptor.setLanguage("en");
        descriptor.setDbConfig(Map.of("db_type", "memory"));
        descriptor.setTransportConfig(transport(memberName));
        return descriptor;
    }

    private static InMemoryTeamDatabase database() {
        InMemoryTeamDatabase db = new InMemoryTeamDatabase();
        db.createTeam("team-a", "Team A", "leader", "desc", "prompt").join();
        db.createMember("dev", "team-a", "Dev", "card", "active").join();
        db.createMember("leader", "team-a", "Leader", "card", "active", "leader", null, null, null, null, null).join();
        db.createMember("peer", "team-a", "Peer", "card", "active").join();
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
