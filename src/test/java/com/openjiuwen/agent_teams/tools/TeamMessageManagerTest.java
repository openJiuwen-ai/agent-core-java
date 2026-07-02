/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.TeamTopic;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Focused parity tests for {@link TeamMessageManager}.
 *
 * <p>Mirrors Python's {@code TeamMessageManager} in
 * {@code openjiuwen/agent_teams/tools/message_manager.py}.</p>
 *
 * <p>Supplemental missing-test coverage mirrors Python's
 * {@code tests/unit_tests/agent_teams/test_message_manager.py}.</p>
 */
class TeamMessageManagerTest {

    private static final String PYTHON_TEAM = "test_team_123";
    private static final String LEADER = "leader";
    private static final String MEMBER_ONE = "member1";
    private static final String MEMBER_TWO = "member2";

    private static final List<String> PYTHON_TESTS = List.of(
            "test_send_message_success",
            "test_send_message_unicode_content",
            "test_send_multiple_messages",
            "test_broadcast_message_success",
            "test_broadcast_multiple_messages",
            "test_get_messages_all",
            "test_get_messages_for_member",
            "test_get_messages_unread_only",
            "test_get_messages_empty",
            "test_get_messages_mixed_broadcast_and_direct",
            "test_get_broadcast_messages_all",
            "test_get_broadcast_messages_unread_only",
            "test_get_broadcast_messages_empty",
            "test_get_broadcast_messages_filters_correctly",
            "test_mark_message_read_success",
            "test_mark_message_read_nonexistent",
            "test_mark_message_read_idempotent",
            "test_mark_broadcast_message_read",
            "test_send_and_get_message_flow",
            "test_broadcast_and_get_flow",
            "test_multi_member_messaging_scenario",
            "test_message_timestamp_ordering",
            "test_team_message_isolation",
            "test_has_unread_messages_direct_unread",
            "test_has_unread_messages_direct_all_read",
            "test_has_unread_messages_broadcast_unread_by_non_sender",
            "test_has_unread_messages_broadcast_read_by_all_non_senders",
            "test_has_unread_messages_empty",
            "test_has_unread_messages_excludes_broadcast",
            "test_has_unread_messages_direct_counts_when_broadcast_excluded"
    );

    @AfterEach
    void resetSessionContext() {
        AgentTeamsContext.resetSessionId(null);
    }

    @TestFactory
    Collection<DynamicTest> pythonMessageManagerCases() {
        return PYTHON_TESTS.stream()
                .map(name -> DynamicTest.dynamicTest(name, () -> runPythonMessageManagerCase(name)))
                .toList();
    }

    private void runPythonMessageManagerCase(String name) {
        switch (name) {
            case "test_send_message_success" -> assertSendMessageSuccess();
            case "test_send_message_unicode_content" -> assertSendMessageUnicodeContent();
            case "test_send_multiple_messages" -> assertSendMultipleMessages();
            case "test_broadcast_message_success" -> assertBroadcastMessageSuccess();
            case "test_broadcast_multiple_messages" -> assertBroadcastMultipleMessages();
            case "test_get_messages_all" -> assertGetMessagesAll();
            case "test_get_messages_for_member" -> assertGetMessagesForMember();
            case "test_get_messages_unread_only" -> assertGetMessagesUnreadOnly();
            case "test_get_messages_empty" -> assertGetMessagesEmpty();
            case "test_get_messages_mixed_broadcast_and_direct" -> assertGetMessagesMixedBroadcastAndDirect();
            case "test_get_broadcast_messages_all" -> assertGetBroadcastMessagesAll();
            case "test_get_broadcast_messages_unread_only" -> assertGetBroadcastMessagesUnreadOnly();
            case "test_get_broadcast_messages_empty" -> assertGetBroadcastMessagesEmpty();
            case "test_get_broadcast_messages_filters_correctly" -> assertGetBroadcastMessagesFiltersCorrectly();
            case "test_mark_message_read_success" -> assertMarkMessageReadSuccess();
            case "test_mark_message_read_nonexistent" -> assertMarkMessageReadNonexistent();
            case "test_mark_message_read_idempotent" -> assertMarkMessageReadIdempotent();
            case "test_mark_broadcast_message_read" -> assertMarkBroadcastMessageRead();
            case "test_send_and_get_message_flow" -> assertSendAndGetMessageFlow();
            case "test_broadcast_and_get_flow" -> assertBroadcastAndGetFlow();
            case "test_multi_member_messaging_scenario" -> assertMultiMemberMessagingScenario();
            case "test_message_timestamp_ordering" -> assertMessageTimestampOrdering();
            case "test_team_message_isolation" -> assertTeamMessageIsolation();
            case "test_has_unread_messages_direct_unread" -> assertHasUnreadMessagesDirectUnread();
            case "test_has_unread_messages_direct_all_read" -> assertHasUnreadMessagesDirectAllRead();
            case "test_has_unread_messages_broadcast_unread_by_non_sender" ->
                    assertHasUnreadMessagesBroadcastUnreadByNonSender();
            case "test_has_unread_messages_broadcast_read_by_all_non_senders" ->
                    assertHasUnreadMessagesBroadcastReadByAllNonSenders();
            case "test_has_unread_messages_empty" -> assertHasUnreadMessagesEmpty();
            case "test_has_unread_messages_excludes_broadcast" -> assertHasUnreadMessagesExcludesBroadcast();
            case "test_has_unread_messages_direct_counts_when_broadcast_excluded" ->
                    assertHasUnreadMessagesDirectCountsWhenBroadcastExcluded();
            default -> throw new IllegalArgumentException("Unhandled Python test: " + name);
        }
    }

    @Test
    void sendMessagePersistsDirectMessageAndPublishesTeamTopic() {
        AgentTeamsContext.setSessionId("session-a");
        InMemoryTeamDatabase database = teamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamMessageManager manager = new TeamMessageManager("team-a", "alice", database, messager);

        String messageId = manager.sendMessage("hello bob", "bob").toCompletableFuture().join();

        assertThat(messageId).isNotBlank();
        assertThat(database.getMessages("team-a", "bob", false, null).join())
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.getMessageId()).isEqualTo(messageId);
                    assertThat(message.getFromMemberName()).isEqualTo("alice");
                    assertThat(message.getToMemberName()).isEqualTo("bob");
                    assertThat(message.getContent()).isEqualTo("hello bob");
                    assertThat(message.getBroadcast()).isFalse();
                    assertThat(message.getIsRead()).isFalse();
                });
        assertThat(messager.publishedTopics).containsExactly(TeamTopic.MESSAGE.build("session-a", "team-a"));
        assertThat(messager.publishedMessages).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo("message");
            assertThat(event.getPayloadData()).containsEntry("message_id", messageId);
            assertThat(event.getPayloadData()).containsEntry("team_name", "team-a");
            assertThat(event.getPayloadData()).containsEntry("from_member_name", "alice");
            assertThat(event.getPayloadData()).containsEntry("to_member_name", "bob");
        });
    }

    @Test
    void sendMessageUsesSenderOverrideAndDoesNotAutoReadHumanRecipient() {
        InMemoryTeamDatabase database = teamDatabase();
        database.createMember(
                "human",
                "team-a",
                "Human",
                "{}",
                MemberStatus.READY.value(),
                TeamRole.HUMAN_AGENT.value(),
                "desc",
                null,
                null,
                null,
                null
        ).join();
        TeamMessageManager manager = new TeamMessageManager("team-a", "alice", database, new RecordingMessager());

        String messageId = manager.sendMessage("needs review", "human", "operator").toCompletableFuture().join();

        assertThat(database.getMessages("team-a", "human", true, "operator").join())
                .extracting(TeamMessage::getMessageId)
                .containsExactly(messageId);
        assertThat(manager.hasUnreadMessages(false).toCompletableFuture().join()).isTrue();
    }

    @Test
    void broadcastMessagePersistsBroadcastWithoutAutoMarkingHumanRecipients() {
        AgentTeamsContext.setSessionId("session-b");
        InMemoryTeamDatabase database = teamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamMessageManager manager = new TeamMessageManager("team-a", "alice", database, messager);

        String messageId = manager.broadcastMessage("standup now").toCompletableFuture().join();

        assertThat(database.getBroadcastMessages("team-a", "bob", true, null).join())
                .extracting(TeamMessage::getMessageId)
                .containsExactly(messageId);
        assertThat(manager.hasUnreadMessages(false).toCompletableFuture().join()).isFalse();
        assertThat(manager.hasUnreadMessages(true).toCompletableFuture().join()).isTrue();
        assertThat(messager.publishedTopics).containsExactly(TeamTopic.MESSAGE.build("session-b", "team-a"));
        assertThat(messager.publishedMessages).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo("broadcast");
            assertThat(event.getPayloadData()).containsEntry("message_id", messageId);
            assertThat(event.getPayloadData()).containsEntry("team_name", "team-a");
            assertThat(event.getPayloadData()).containsEntry("from_member_name", "alice");
        });
    }

    @Test
    void publishFailureIsSwallowedAfterDatabaseWrite() {
        InMemoryTeamDatabase database = teamDatabase();
        RecordingMessager messager = new RecordingMessager();
        messager.failPublish = true;
        TeamMessageManager manager = new TeamMessageManager("team-a", "alice", database, messager);

        String messageId = manager.sendMessage("still saved", "bob").toCompletableFuture().join();

        assertThat(messageId).isNotBlank();
        assertThat(database.getMessages("team-a", "bob", false, null).join())
                .extracting(TeamMessage::getMessageId)
                .containsExactly(messageId);
        assertThat(messager.publishedTopics).containsExactly(TeamTopic.MESSAGE.build("", "team-a"));
    }

    @Test
    void createFailureReturnsNullAndDoesNotPublish() {
        RecordingMessager messager = new RecordingMessager();
        TeamMessageManager.MessageStore failingStore = failingCreateStore();
        TeamMessageManager manager = new TeamMessageManager("team-a", "alice", failingStore, messager);

        String messageId = manager.sendMessage("not saved", "bob").toCompletableFuture().join();

        assertThat(messageId).isNull();
        assertThat(messager.publishedTopics).isEmpty();
    }

    @Test
    void gettersAndMarkReadDelegateToMessageStore() {
        InMemoryTeamDatabase database = teamDatabase();
        TeamMessageManager manager = new TeamMessageManager("team-a", "alice", database, new RecordingMessager());
        String first = manager.sendMessage("first", "bob").toCompletableFuture().join();
        String second = manager.sendMessage("second", "bob", "carol").toCompletableFuture().join();
        String broadcast = manager.broadcastMessage("all hands", "carol").toCompletableFuture().join();

        assertThat(manager.getMessages("bob", false, "carol").toCompletableFuture().join())
                .extracting(TeamMessage::getMessageId)
                .containsExactly(second);
        assertThat(manager.getTeamMessages("team-a").toCompletableFuture().join())
                .extracting(TeamMessage::getMessageId)
                .containsExactly(first, second, broadcast);

        assertThat(manager.markMessageRead(first, "bob").toCompletableFuture().join()).isTrue();

        assertThat(manager.getMessages("bob", true, null).toCompletableFuture().join())
                .extracting(TeamMessage::getMessageId)
                .containsExactly(second);
        assertThat(manager.getBroadcastMessages("bob", true, "carol").toCompletableFuture().join())
                .extracting(TeamMessage::getMessageId)
                .containsExactly(broadcast);
    }

    private static InMemoryTeamDatabase teamDatabase() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        database.createMember("alice", "team-a", "Alice", "{}", MemberStatus.READY.value()).join();
        database.createMember("bob", "team-a", "Bob", "{}", MemberStatus.READY.value()).join();
        database.createMember("carol", "team-a", "Carol", "{}", MemberStatus.READY.value()).join();
        return database;
    }

    private static void assertSendMessageSuccess() {
        PythonFixture fixture = pythonFixture();

        String messageId = join(fixture.manager.sendMessage("Hello member2", MEMBER_TWO));
        List<TeamMessage> messages = join(fixture.manager.getMessages(MEMBER_TWO));

        assertThat(messageId).isNotBlank();
        assertThat(messages).singleElement().satisfies(message -> {
            assertThat(message.getMessageId()).isEqualTo(messageId);
            assertThat(message.getContent()).isEqualTo("Hello member2");
            assertThat(message.getFromMemberName()).isEqualTo(MEMBER_ONE);
            assertThat(message.getToMemberName()).isEqualTo(MEMBER_TWO);
            assertThat(message.getBroadcast()).isFalse();
            assertThat(message.getIsRead()).isFalse();
        });
    }

    private static void assertSendMessageUnicodeContent() {
        PythonFixture fixture = pythonFixture();

        join(fixture.manager.sendMessage("你好，世界！🎉", MEMBER_TWO));

        assertThat(join(fixture.manager.getMessages(MEMBER_TWO)))
                .singleElement()
                .extracting(TeamMessage::getContent)
                .isEqualTo("你好，世界！🎉");
    }

    private static void assertSendMultipleMessages() {
        PythonFixture fixture = pythonFixture();
        List<String> messageIds = new ArrayList<>();

        for (int index = 0; index < 5; index++) {
            String messageId = join(fixture.manager.sendMessage("Message " + index, "receiver_0"));
            assertThat(messageId).isNotBlank();
            messageIds.add(messageId);
        }

        List<TeamMessage> messages = join(fixture.manager.getMessages("receiver_0"));
        assertThat(messages).hasSize(5);
        for (int index = 0; index < messages.size(); index++) {
            assertThat(messages.get(index).getContent()).isEqualTo("Message " + index);
            assertThat(messages.get(index).getMessageId()).isEqualTo(messageIds.get(index));
        }
    }

    private static void assertBroadcastMessageSuccess() {
        PythonFixture fixture = pythonFixture();
        TeamMessageManager leaderManager = managerFor(fixture, LEADER);

        String messageId = join(leaderManager.broadcastMessage("Team meeting at 3PM"));
        List<TeamMessage> broadcasts = join(fixture.manager.getBroadcastMessages(MEMBER_ONE));

        assertThat(messageId).isNotBlank();
        assertThat(broadcasts).singleElement().satisfies(message -> {
            assertThat(message.getMessageId()).isEqualTo(messageId);
            assertThat(message.getContent()).isEqualTo("Team meeting at 3PM");
            assertThat(message.getFromMemberName()).isEqualTo(LEADER);
            assertThat(message.getToMemberName()).isNull();
            assertThat(message.getBroadcast()).isTrue();
            assertThat(message.getIsRead()).isNotEqualTo(Boolean.TRUE);
        });
    }

    private static void assertBroadcastMultipleMessages() {
        PythonFixture fixture = pythonFixture();
        List<String> senders = List.of(LEADER, MEMBER_ONE, MEMBER_TWO);
        List<String> messageIds = new ArrayList<>();

        for (String sender : senders) {
            messageIds.add(join(managerFor(fixture, sender).broadcastMessage("Announcement from " + sender)));
        }

        List<TeamMessage> broadcasts = join(fixture.manager.getBroadcastMessages(MEMBER_TWO));
        assertThat(broadcasts).hasSize(2);
        for (int index = 0; index < broadcasts.size(); index++) {
            assertThat(broadcasts.get(index).getBroadcast()).isTrue();
            assertThat(broadcasts.get(index).getFromMemberName()).isEqualTo(senders.get(index));
            assertThat(broadcasts.get(index).getMessageId()).isEqualTo(messageIds.get(index));
        }
    }

    private static void assertGetMessagesAll() {
        PythonFixture fixture = pythonFixture();
        join(fixture.manager.sendMessage("Msg1", MEMBER_TWO));
        join(fixture.manager.sendMessage("Msg2", MEMBER_TWO));

        List<TeamMessage> messages = join(fixture.manager.getMessages(MEMBER_TWO));

        assertThat(messages).hasSize(2);
        assertThat(messages).allMatch(TeamMessage.class::isInstance);
        assertThat(messages).extracting(TeamMessage::getContent).containsExactly("Msg1", "Msg2");
    }

    private static void assertGetMessagesForMember() {
        PythonFixture fixture = pythonFixture();
        join(fixture.manager.sendMessage("To member2", MEMBER_TWO));
        join(fixture.manager.sendMessage("To member1", MEMBER_ONE));
        join(fixture.manager.sendMessage("Also to member2", MEMBER_TWO));

        List<TeamMessage> messages = join(fixture.manager.getMessages(MEMBER_TWO));

        assertThat(messages).hasSize(2);
        assertThat(messages).allSatisfy(message -> assertThat(message.getToMemberName()).isEqualTo(MEMBER_TWO));
    }

    private static void assertGetMessagesUnreadOnly() {
        PythonFixture fixture = pythonFixture();
        String unread = join(fixture.manager.sendMessage("Unread message", MEMBER_TWO));
        String read = join(fixture.manager.sendMessage("Read message", MEMBER_TWO));
        join(fixture.manager.markMessageRead(read, MEMBER_TWO));

        List<TeamMessage> messages = join(fixture.manager.getMessages(MEMBER_TWO, true));

        assertThat(messages).singleElement().satisfies(message -> {
            assertThat(message.getMessageId()).isEqualTo(unread);
            assertThat(message.getIsRead()).isFalse();
        });
    }

    private static void assertGetMessagesEmpty() {
        PythonFixture fixture = pythonFixture();

        assertThat(join(fixture.manager.getMessages(MEMBER_TWO))).isEmpty();
    }

    private static void assertGetMessagesMixedBroadcastAndDirect() {
        PythonFixture fixture = pythonFixture();
        join(fixture.manager.sendMessage("Direct message", MEMBER_TWO));
        join(fixture.manager.broadcastMessage("Broadcast"));

        List<TeamMessage> directMessages = join(fixture.manager.getMessages(MEMBER_TWO));

        assertThat(directMessages).singleElement()
                .extracting(TeamMessage::getBroadcast)
                .isEqualTo(false);
    }

    private static void assertGetBroadcastMessagesAll() {
        PythonFixture fixture = pythonFixture();
        join(fixture.manager.broadcastMessage("Announcement 1"));
        join(fixture.manager.broadcastMessage("Announcement 2"));

        List<TeamMessage> messages = join(fixture.manager.getBroadcastMessages(MEMBER_TWO));

        assertThat(messages).hasSize(2);
        assertThat(messages).allSatisfy(message -> {
            assertThat(message.getBroadcast()).isTrue();
            assertThat(message.getToMemberName()).isNull();
        });
    }

    private static void assertGetBroadcastMessagesUnreadOnly() {
        PythonFixture fixture = pythonFixture();
        String oldMessage = join(fixture.manager.broadcastMessage("Old announcement"));
        Long oldTimestamp = join(fixture.manager.getBroadcastMessages(MEMBER_TWO))
                .stream()
                .filter(message -> oldMessage.equals(message.getMessageId()))
                .findFirst()
                .orElseThrow()
                .getTimestamp();
        pausePastTimestamp(oldTimestamp);
        String newMessage = join(fixture.manager.broadcastMessage("New announcement"));
        join(fixture.manager.markMessageRead(oldMessage, MEMBER_TWO));

        List<TeamMessage> messages = join(fixture.manager.getBroadcastMessages(MEMBER_TWO, true));
        String expectedNewMessage = newMessage;

        assertThat(messages).singleElement();
        TeamMessage message = messages.get(0);
        assertThat(message.getMessageId()).isEqualTo(expectedNewMessage);
        assertThat(message.getIsRead()).isNotEqualTo(Boolean.TRUE);
    }

    private static void assertGetBroadcastMessagesEmpty() {
        PythonFixture fixture = pythonFixture();
        join(fixture.manager.sendMessage("Direct message", MEMBER_ONE));

        assertThat(join(fixture.manager.getBroadcastMessages(MEMBER_TWO))).isEmpty();
    }

    private static void assertGetBroadcastMessagesFiltersCorrectly() {
        PythonFixture fixture = pythonFixture();
        join(fixture.manager.sendMessage("Direct 1", MEMBER_ONE));
        join(fixture.manager.broadcastMessage("Broadcast 1"));
        join(fixture.manager.sendMessage("Direct 2", MEMBER_TWO));
        join(fixture.manager.broadcastMessage("Broadcast 2"));

        List<TeamMessage> broadcasts = join(fixture.manager.getBroadcastMessages(MEMBER_TWO));

        assertThat(broadcasts).hasSize(2);
        assertThat(broadcasts).allSatisfy(message -> {
            assertThat(message.getBroadcast()).isTrue();
            assertThat(message.getToMemberName()).isNull();
        });
    }

    private static void assertMarkMessageReadSuccess() {
        PythonFixture fixture = pythonFixture();
        String messageId = join(fixture.manager.sendMessage("Hello", MEMBER_TWO));

        Boolean result = join(fixture.manager.markMessageRead(messageId, MEMBER_TWO));

        assertThat(result).isTrue();
        assertThat(join(fixture.manager.getMessages(MEMBER_TWO)))
                .singleElement()
                .extracting(TeamMessage::getIsRead)
                .isEqualTo(true);
    }

    private static void assertMarkMessageReadNonexistent() {
        PythonFixture fixture = pythonFixture();

        assertThat(join(fixture.manager.markMessageRead("nonexistent_msg", MEMBER_TWO))).isFalse();
    }

    private static void assertMarkMessageReadIdempotent() {
        PythonFixture fixture = pythonFixture();
        String messageId = join(fixture.manager.sendMessage("Hello", MEMBER_TWO));

        assertThat(join(fixture.manager.markMessageRead(messageId, MEMBER_TWO))).isTrue();
        assertThat(join(fixture.manager.markMessageRead(messageId, MEMBER_TWO))).isTrue();
        assertThat(join(fixture.manager.getMessages(MEMBER_TWO)))
                .singleElement()
                .extracting(TeamMessage::getIsRead)
                .isEqualTo(true);
    }

    private static void assertMarkBroadcastMessageRead() {
        PythonFixture fixture = pythonFixture();
        String messageId = join(fixture.manager.broadcastMessage("Announcement"));

        Boolean result = join(fixture.manager.markMessageRead(messageId, MEMBER_TWO));
        List<TeamMessage> broadcasts = join(fixture.manager.getBroadcastMessages(MEMBER_TWO));

        assertThat(result).isTrue();
        assertThat(broadcasts).singleElement().satisfies(message -> {
            assertThat(message.getBroadcast()).isTrue();
            assertThat(message.getIsRead()).isNotEqualTo(Boolean.TRUE);
        });
    }

    private static void assertSendAndGetMessageFlow() {
        PythonFixture fixture = pythonFixture();
        String messageId = join(fixture.manager.sendMessage("Hello", MEMBER_TWO));

        assertThat(join(fixture.manager.getMessages(MEMBER_TWO, true)))
                .singleElement()
                .extracting(TeamMessage::getMessageId)
                .isEqualTo(messageId);
        assertThat(join(fixture.manager.markMessageRead(messageId, MEMBER_TWO))).isTrue();
        assertThat(join(fixture.manager.getMessages(MEMBER_TWO, true))).isEmpty();
    }

    private static void assertBroadcastAndGetFlow() {
        PythonFixture fixture = pythonFixture();
        String messageId = join(fixture.manager.broadcastMessage("Team meeting at 3PM"));

        List<TeamMessage> broadcasts = join(fixture.manager.getBroadcastMessages(MEMBER_TWO));

        assertThat(broadcasts).singleElement().satisfies(message -> {
            assertThat(message.getBroadcast()).isTrue();
            assertThat(message.getMessageId()).isEqualTo(messageId);
        });
        assertThat(join(fixture.manager.markMessageRead(messageId, MEMBER_TWO))).isTrue();
        assertThat(join(fixture.manager.getBroadcastMessages(MEMBER_TWO, true))).isEmpty();
    }

    private static void assertMultiMemberMessagingScenario() {
        PythonFixture fixture = pythonFixture();
        List<String> members = List.of(MEMBER_ONE, MEMBER_TWO, "member3");

        for (String sender : members) {
            TeamMessageManager manager = managerFor(fixture, sender);
            for (String recipient : members) {
                if (!sender.equals(recipient)) {
                    join(manager.sendMessage("Hello from " + sender + " to " + recipient, recipient));
                }
            }
        }
        join(fixture.manager.broadcastMessage("Welcome to the team!"));

        assertThat(join(fixture.manager.getMessages(MEMBER_TWO))).hasSize(2);
        assertThat(join(fixture.manager.getBroadcastMessages(MEMBER_TWO)))
                .singleElement()
                .extracting(TeamMessage::getContent)
                .isEqualTo("Welcome to the team!");
    }

    private static void assertMessageTimestampOrdering() {
        PythonFixture fixture = pythonFixture();
        String first = join(fixture.manager.sendMessage("First", MEMBER_TWO));
        String second = join(fixture.manager.sendMessage("Second", MEMBER_ONE));
        String third = join(fixture.manager.sendMessage("Third", MEMBER_TWO));

        assertThat(join(fixture.manager.getTeamMessages(PYTHON_TEAM)))
                .extracting(TeamMessage::getMessageId)
                .containsExactly(first, second, third);
    }

    private static void assertTeamMessageIsolation() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        join(database.createTeam("team1", "Team 1", "leader1", null, null));
        join(database.createTeam("team2", "Team 2", "leader2", null, null));
        TeamMessageManager messagingOne = new TeamMessageManager("team1", "leader1", database, messager);
        TeamMessageManager messagingTwo = new TeamMessageManager("team2", "leader2", database, messager);

        join(messagingOne.sendMessage("Team 1 message", MEMBER_ONE));
        join(messagingTwo.sendMessage("Team 2 message", MEMBER_ONE));

        assertThat(join(messagingOne.getMessages(MEMBER_ONE)))
                .singleElement()
                .extracting(TeamMessage::getContent)
                .isEqualTo("Team 1 message");
        assertThat(join(messagingTwo.getMessages(MEMBER_ONE)))
                .singleElement()
                .extracting(TeamMessage::getContent)
                .isEqualTo("Team 2 message");
    }

    private static void assertHasUnreadMessagesDirectUnread() {
        PythonFixture fixture = pythonFixture();

        join(fixture.manager.sendMessage("ping", MEMBER_TWO));

        assertThat(join(fixture.manager.hasUnreadMessages())).isTrue();
    }

    private static void assertHasUnreadMessagesDirectAllRead() {
        PythonFixture fixture = pythonFixture();
        String messageId = join(fixture.manager.sendMessage("ping", MEMBER_TWO));
        join(fixture.manager.markMessageRead(messageId, MEMBER_TWO));

        assertThat(join(fixture.manager.hasUnreadMessages())).isFalse();
    }

    private static void assertHasUnreadMessagesBroadcastUnreadByNonSender() {
        PythonFixture fixture = pythonFixture();

        join(fixture.manager.broadcastMessage("all hands"));

        assertThat(join(fixture.manager.hasUnreadMessages())).isTrue();
    }

    private static void assertHasUnreadMessagesBroadcastReadByAllNonSenders() {
        PythonFixture fixture = pythonFixture();
        String messageId = join(fixture.manager.broadcastMessage("all hands"));
        join(fixture.manager.markMessageRead(messageId, MEMBER_TWO));

        assertThat(join(fixture.manager.hasUnreadMessages())).isFalse();
    }

    private static void assertHasUnreadMessagesEmpty() {
        PythonFixture fixture = pythonFixture();

        assertThat(join(fixture.manager.hasUnreadMessages())).isFalse();
    }

    private static void assertHasUnreadMessagesExcludesBroadcast() {
        PythonFixture fixture = pythonFixture();

        join(fixture.manager.broadcastMessage("all hands"));

        assertThat(join(fixture.manager.hasUnreadMessages())).isTrue();
        assertThat(join(fixture.manager.hasUnreadMessages(false))).isFalse();
    }

    private static void assertHasUnreadMessagesDirectCountsWhenBroadcastExcluded() {
        PythonFixture fixture = pythonFixture();

        join(fixture.manager.sendMessage("ping", MEMBER_TWO));

        assertThat(join(fixture.manager.hasUnreadMessages())).isTrue();
        assertThat(join(fixture.manager.hasUnreadMessages(false))).isTrue();
    }

    private static PythonFixture pythonFixture() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        join(database.createTeam(PYTHON_TEAM, "Test Team", LEADER, null, null));
        join(database.createMember(MEMBER_ONE, PYTHON_TEAM, "Member One", "{\"name\":\"TestAgent\"}",
                MemberStatus.BUSY.value()));
        join(database.createMember(MEMBER_TWO, PYTHON_TEAM, "Member Two", "{\"name\":\"TestAgent\"}",
                MemberStatus.BUSY.value()));
        RecordingMessager messager = new RecordingMessager();
        return new PythonFixture(database, messager, new TeamMessageManager(PYTHON_TEAM, MEMBER_ONE, database, messager));
    }

    private static TeamMessageManager managerFor(PythonFixture fixture, String memberName) {
        return new TeamMessageManager(PYTHON_TEAM, memberName, fixture.database, fixture.messager);
    }

    private static void pausePastTimestamp(Long timestamp) {
        long deadline = System.currentTimeMillis() + 500L;
        try {
            while (InMemoryTeamDatabase.getCurrentTime() <= timestamp && System.currentTimeMillis() < deadline) {
                Thread.sleep(1L);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while separating message timestamps", exception);
        }
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private record PythonFixture(
            InMemoryTeamDatabase database,
            RecordingMessager messager,
            TeamMessageManager manager) {
    }

    private static TeamMessageManager.MessageStore failingCreateStore() {
        return new TeamMessageManager.MessageStore() {
            @Override
            public CompletionStage<Boolean> createMessage(
                    String messageId,
                    String teamName,
                    String fromMemberName,
                    String content,
                    String toMemberName,
                    boolean broadcast,
                    boolean isRead) {
                return CompletableFuture.completedFuture(false);
            }

            @Override
            public CompletionStage<List<TeamMessage>> getMessages(
                    String teamName,
                    String toMemberName,
                    boolean unreadOnly,
                    String fromMemberName) {
                return CompletableFuture.completedFuture(List.of());
            }

            @Override
            public CompletionStage<List<TeamMessage>> getBroadcastMessages(
                    String teamName,
                    String memberName,
                    boolean unreadOnly,
                    String fromMemberName) {
                return CompletableFuture.completedFuture(List.of());
            }

            @Override
            public CompletionStage<List<TeamMessage>> getTeamMessages(String teamName, Boolean broadcast) {
                return CompletableFuture.completedFuture(List.of());
            }

            @Override
            public CompletionStage<Boolean> hasUnreadMessages(String teamName, boolean includeBroadcast) {
                return CompletableFuture.completedFuture(false);
            }

            @Override
            public CompletionStage<Boolean> markMessageRead(String messageId, String memberName) {
                return CompletableFuture.completedFuture(false);
            }
        };
    }

    /**
     * Recording messager collaborator for {@link TeamMessageManager} tests.
     *
     * <p>Mirrors Python's {@code Messager} dependency used by
     * {@code openjiuwen/agent_teams/tools/message_manager.py}.</p>
     */
    private static final class RecordingMessager implements Messager {
        private final List<String> publishedTopics = new ArrayList<>();
        private final List<EventMessage> publishedMessages = new ArrayList<>();
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
            publishedTopics.add(topicId);
            publishedMessages.add(message);
            if (failPublish) {
                return CompletableFuture.failedFuture(new IllegalStateException("publish boom"));
            }
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
