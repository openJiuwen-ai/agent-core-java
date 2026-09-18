/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link UserInbox}.
 *
 * <p>Mirrors Python's {@code UserInbox} behavior in
 * {@code openjiuwen/agent_teams/interaction/user_inbox.py}.</p>
 */
class UserInboxTest {

    @Test
    void directWritesPointToPointMessageAsUser() {
        FakeMessageManager messages = new FakeMessageManager("direct-1", "broadcast-1");
        UserInbox inbox = new UserInbox(messages);

        DeliverResult result = inbox.direct("alice", "look at this").toCompletableFuture().join();

        assertThat(result).isEqualTo(DeliverResult.success("direct-1"));
        assertThat(messages.directMessages)
                .containsExactly(new MessageRecord("look at this", "alice", "user"));
    }

    @Test
    void directReturnsStableFailureWhenBusRejects() {
        FakeMessageManager messages = new FakeMessageManager(null, "broadcast-1");
        UserInbox inbox = new UserInbox(messages);

        DeliverResult result = inbox.direct("alice", "look at this").toCompletableFuture().join();

        assertThat(result).isEqualTo(DeliverResult.failure("send_failed:alice"));
    }

    @Test
    void broadcastWritesTeamWideMessageAsUser() {
        FakeMessageManager messages = new FakeMessageManager("direct-1", "broadcast-1");
        UserInbox inbox = new UserInbox(messages);

        DeliverResult result = inbox.broadcast("everyone read this").toCompletableFuture().join();

        assertThat(result).isEqualTo(DeliverResult.success("broadcast-1"));
        assertThat(messages.broadcasts)
                .containsExactly(new MessageRecord("everyone read this", null, "user"));
    }

    @Test
    void broadcastReturnsStableFailureWhenBusRejects() {
        FakeMessageManager messages = new FakeMessageManager("direct-1", null);
        UserInbox inbox = new UserInbox(messages);

        DeliverResult result = inbox.broadcast("everyone read this").toCompletableFuture().join();

        assertThat(result).isEqualTo(DeliverResult.failure("broadcast_failed"));
    }

    @Test
    void deliverToLeaderForwardsBodyAndReturnsSuccessWithoutMessageId() {
        List<String> delivered = new ArrayList<>();

        DeliverResult result = UserInbox.deliverToLeader(body -> {
            delivered.add(body);
            return CompletableFuture.completedFuture(null);
        }, "plain input").toCompletableFuture().join();

        assertThat(result).isEqualTo(DeliverResult.success());
        assertThat(delivered).containsExactly("plain input");
    }

    @Test
    void deliverToLeaderConvertsAsyncExceptionToFailureToken() {
        DeliverResult result = UserInbox.deliverToLeader(
                body -> CompletableFuture.failedFuture(new IllegalStateException("boom")),
                "plain input"
        ).toCompletableFuture().join();

        assertThat(result).isEqualTo(DeliverResult.failure("deliver_to_leader_failed:boom"));
    }

    @Test
    void deliverToLeaderConvertsSynchronousExceptionToFailureToken() {
        DeliverResult result = UserInbox.deliverToLeader(body -> {
            throw new IllegalArgumentException("sync boom");
        }, "plain input").toCompletableFuture().join();

        assertThat(result).isEqualTo(DeliverResult.failure("deliver_to_leader_failed:sync boom"));
    }

    private record MessageRecord(String content, String toMemberName, String fromMemberName) {
    }

    private static final class FakeMessageManager implements UserInbox.MessageManagerView {
        private final String directMessageId;
        private final String broadcastMessageId;
        private final List<MessageRecord> directMessages = new ArrayList<>();
        private final List<MessageRecord> broadcasts = new ArrayList<>();

        private FakeMessageManager(String directMessageId, String broadcastMessageId) {
            this.directMessageId = directMessageId;
            this.broadcastMessageId = broadcastMessageId;
        }

        @Override
        public CompletionStage<String> broadcastMessage(String content, String fromMemberName) {
            broadcasts.add(new MessageRecord(content, null, fromMemberName));
            return CompletableFuture.completedFuture(broadcastMessageId);
        }

        @Override
        public CompletionStage<String> sendMessage(String content, String toMemberName, String fromMemberName) {
            directMessages.add(new MessageRecord(content, toMemberName, fromMemberName));
            return CompletableFuture.completedFuture(directMessageId);
        }
    }
}
