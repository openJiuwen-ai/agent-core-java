/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agent_teams.interaction.HumanAgentInbox.AgentRuntime;
import com.openjiuwen.agent_teams.interaction.HumanAgentInbox.MessageManagerView;
import com.openjiuwen.agent_teams.interaction.HumanAgentInbox.OnInbound;
import com.openjiuwen.agent_teams.interaction.HumanAgentInbox.TeamBackendView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link HumanAgentInbox}.
 *
 * <p>Mirrors Python's {@code test_human_agent_inbox.py} for
 * {@code openjiuwen/agent_teams/interaction/human_agent_inbox.py}.</p>
 */
class HumanAgentInboxTest {

    private static final String HUMAN = "human_alice";
    private static final String TEAMMATE = "dev_bob";

    @Test
    void sendToNoneDrivesAvatar() {
        FakeTeamBackend team = hittTeam();
        FakeMessageManager messages = new FakeMessageManager();
        FakeAgent avatar = new FakeAgent();
        HumanAgentInbox inbox = new HumanAgentInbox(team, messages, memberName -> completed(avatar));

        DeliverResult result = inbox.send("read design.md and summarise it").toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(result.messageId()).isNull();
        assertThat(avatar.deliveredBodies).containsExactly("read design.md and summarise it");
    }

    @Test
    void sendInboxDoesNotParseAtInBody() {
        FakeTeamBackend team = hittTeam();
        FakeMessageManager messages = new FakeMessageManager();
        FakeAgent avatar = new FakeAgent();
        HumanAgentInbox inbox = new HumanAgentInbox(team, messages, memberName -> completed(avatar));

        DeliverResult result = inbox.send("@" + TEAMMATE + " ping me when done").toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(avatar.deliveredBodies).containsExactly("@" + TEAMMATE + " ping me when done");
    }

    @Test
    void sendToMemberRoutesDirect() {
        FakeTeamBackend team = hittTeam();
        FakeMessageManager messages = new FakeMessageManager();
        HumanAgentInbox inbox = new HumanAgentInbox(team, messages);

        DeliverResult result = inbox.send("ping me when done", TEAMMATE).toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(result.messageId()).isEqualTo("direct-1");
        assertThat(messages.directMessages).containsExactly(new MessageRecord("ping me when done", TEAMMATE, HUMAN));
    }

    @Test
    void sendToAllBroadcastsAsHumanAgent() {
        FakeTeamBackend team = hittTeam();
        FakeMessageManager messages = new FakeMessageManager();
        HumanAgentInbox inbox = new HumanAgentInbox(team, messages);

        DeliverResult first = inbox.send("status sync", "all").toCompletableFuture().join();
        DeliverResult star = inbox.send("heads up", "*").toCompletableFuture().join();

        assertThat(first.ok()).isTrue();
        assertThat(star.ok()).isTrue();
        assertThat(messages.broadcasts).containsExactly(
                new MessageRecord("status sync", null, HUMAN),
                new MessageRecord("heads up", null, HUMAN)
        );
    }

    @Test
    void sendToUnknownMemberReturnsUnknownMember() {
        HumanAgentInbox inbox = new HumanAgentInbox(hittTeam(), new FakeMessageManager());

        DeliverResult result = inbox.send("hi", "ghost").toCompletableFuture().join();

        assertThat(result.ok()).isFalse();
        assertThat(result.reason()).isEqualTo("unknown_member:ghost");
    }

    @Test
    void sendNoAgentLookupReturnsAgentUnavailable() {
        HumanAgentInbox inbox = new HumanAgentInbox(hittTeam(), new FakeMessageManager());

        DeliverResult result = inbox.send("do the thing").toCompletableFuture().join();

        assertThat(result.ok()).isFalse();
        assertThat(result.reason()).isEqualTo("agent_unavailable");
    }

    @Test
    void sendUnknownSenderRaises() {
        HumanAgentInbox inbox = new HumanAgentInbox(hittTeam(), new FakeMessageManager(), memberName -> completed(null));

        assertThatThrownBy(() -> inbox.send("hi", null, "nope").toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(UnknownHumanAgentError.class);
    }

    @Test
    void sendNoHumanAgentRegisteredRaises() {
        HumanAgentInbox inbox = new HumanAgentInbox(new FakeTeamBackend(List.of(), List.of()), new FakeMessageManager());

        assertThatThrownBy(() -> inbox.send("hello").toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(HumanAgentNotEnabledError.class);
    }

    @Test
    void omittedSenderFallsBackToSortedHumanAgentWhenReservedAbsent() {
        FakeTeamBackend team = new FakeTeamBackend(List.of("zoe", "ann"), List.of(TEAMMATE));
        FakeMessageManager messages = new FakeMessageManager();
        HumanAgentInbox inbox = new HumanAgentInbox(team, messages);

        DeliverResult result = inbox.send("hello", TEAMMATE).toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(messages.directMessages).containsExactly(new MessageRecord("hello", TEAMMATE, "ann"));
    }

    @Test
    void onInboundAccessorReturnsRegisteredCallback() {
        OnInbound callback = event -> CompletableFuture.completedFuture(null);
        HumanAgentInbox inbox = new HumanAgentInbox(hittTeam(), new FakeMessageManager(), null, callback);

        assertThat(inbox.getOnInbound()).isSameAs(callback);
    }

    private static FakeTeamBackend hittTeam() {
        return new FakeTeamBackend(List.of(HUMAN), List.of(TEAMMATE, HUMAN));
    }

    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private record MessageRecord(String content, String toMemberName, String fromMemberName) {
    }

    private static final class FakeTeamBackend implements TeamBackendView {
        private final List<String> humans;
        private final List<String> members;

        FakeTeamBackend(List<String> humans, List<String> members) {
            this.humans = humans;
            this.members = members;
        }

        @Override
        public CompletionStage<List<String>> humanAgentNames() {
            return completed(humans);
        }

        @Override
        public CompletionStage<Object> getMember(String name) {
            return completed(members.contains(name) ? name : null);
        }
    }

    private static final class FakeMessageManager implements MessageManagerView {
        private final List<MessageRecord> broadcasts = new ArrayList<>();
        private final List<MessageRecord> directMessages = new ArrayList<>();

        @Override
        public CompletionStage<String> broadcastMessage(String content, String fromMemberName) {
            broadcasts.add(new MessageRecord(content, null, fromMemberName));
            return completed("broadcast-" + broadcasts.size());
        }

        @Override
        public CompletionStage<String> sendMessage(String content, String toMemberName, String fromMemberName) {
            directMessages.add(new MessageRecord(content, toMemberName, fromMemberName));
            return completed("direct-" + directMessages.size());
        }
    }

    private static final class FakeAgent implements AgentRuntime {
        private final List<String> deliveredBodies = new ArrayList<>();

        @Override
        public CompletionStage<Void> deliverInput(String body) {
            deliveredBodies.add(body);
            return CompletableFuture.completedFuture(null);
        }
    }
}
