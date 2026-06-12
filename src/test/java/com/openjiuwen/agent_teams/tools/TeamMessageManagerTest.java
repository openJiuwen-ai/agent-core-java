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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link TeamMessageManager}.
 *
 * <p>Mirrors Python's {@code TeamMessageManager} in
 * {@code openjiuwen/agent_teams/tools/message_manager.py}.</p>
 */
class TeamMessageManagerTest {

    @AfterEach
    void resetSessionContext() {
        AgentTeamsContext.resetSessionId(null);
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
