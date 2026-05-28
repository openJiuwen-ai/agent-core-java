package com.openjiuwen.agent_teams.messager;

import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.agent_teams.tools.TeamBackendRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.test_messager}
 * and {@code tests.unit_tests.agent_teams.worktree.test_backend}.
 * Tests for InProcessMessager pub/sub, TeamBackend message persistence, and task storage.
 */
class AgentTeamsTransportAndBackendTest {

    @AfterEach
    void cleanup() {
        InProcessMessager.cleanupBus();
        TeamBackendRegistry.clear();
    }

    @Test
    void inprocessMessagerSupportsPubsubAndDirectDelivery() {
        List<EventMessage> topicReceived = new ArrayList<>();
        List<EventMessage> directReceived = new ArrayList<>();

        MessagerTransportConfig publisherConfig = new MessagerTransportConfig();
        publisherConfig.setNodeId("leader");
        InProcessMessager publisher = new InProcessMessager(publisherConfig);

        MessagerTransportConfig subscriberConfig = new MessagerTransportConfig();
        subscriberConfig.setNodeId("worker");
        InProcessMessager subscriber = new InProcessMessager(subscriberConfig);

        subscriber.subscribe("topic:team", topicReceived::add);
        subscriber.registerDirectMessageHandler(directReceived::add);

        EventMessage pubsubEvent = new EventMessage("team_cleaned", java.util.Map.of("team_name", "team-1"));
        publisher.publish("topic:team", pubsubEvent);

        EventMessage directEvent = new EventMessage("member_spawned", java.util.Map.of("member_name", "worker"));
        publisher.send("worker", directEvent);

        assertEquals(1, topicReceived.size());
        assertEquals("leader", topicReceived.get(0).getSenderId());
        assertEquals(1, directReceived.size());
        assertEquals("leader", directReceived.get(0).getSenderId());
    }

    @Test
    void teamBackendsForSameTeamShareStoreAndPersistMessagesAndTasks() {
        TeamBackend leaderBackend = new TeamBackend("team-x", "leader", true, null, List.of());
        TeamBackend workerBackend = new TeamBackend("team-x", "worker", false, null, List.of());

        TeamMemberSpec memberSpec = new TeamMemberSpec();
        memberSpec.setMemberName("worker");
        memberSpec.setDisplayName("worker");
        memberSpec.setRoleType(TeamRole.TEAMMATE);
        leaderBackend.spawnMember(
                memberSpec.getMemberName(),
                memberSpec.getDisplayName(),
                null,
                memberSpec.getPersona(),
                memberSpec.getPromptHint(),
                MemberStatus.UNSTARTED,
                ExecutionStatus.IDLE
        );

        String messageId = leaderBackend.sendMessage("hello", "worker", "leader");
        assertEquals(1, workerBackend.getMessages("worker", false, null).size());
        assertEquals(messageId, workerBackend.getMessages("worker", false, null).get(0).getMessageId());

        leaderBackend.createTask("title", "content", "task-1", List.of());
        assertEquals(1, workerBackend.listTasks().size());
        assertEquals("task-1", workerBackend.listTasks().get(0).getTaskId());

        assertSame(leaderBackend.getMessager(), workerBackend.getMessager());
        assertNotNull(workerBackend.getMessager());
    }

    @Test
    void backendBroadcastMessageIsStoredAndReadableAcrossInstances() {
        TeamBackend leaderBackend = new TeamBackend("team-y", "leader", true, null, List.of());
        TeamBackend observerBackend = new TeamBackend("team-y", "observer", false, null, List.of());

        String messageId = leaderBackend.broadcastMessage("notice", "leader");

        assertEquals(1, observerBackend.getBroadcastMessages(false, null).size());
        assertEquals(messageId, observerBackend.getBroadcastMessages(false, null).get(0).getMessageId());
        assertTrue(observerBackend.getBroadcastMessages(false, null).get(0).isBroadcast());
    }
}
