/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.interaction.HumanAgentInbox.AgentRuntime;
import com.openjiuwen.agent_teams.interaction.HumanAgentInbox.MessageManagerView;
import com.openjiuwen.agent_teams.interaction.HumanAgentInbox.TeamBackendView;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.tools.InMemoryTeamDatabase;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/interaction/test_human_agent_inbox.py}.
 */
class HumanAgentInboxPythonParityTest {

    private static final String HUMAN = "human_alice";
    private static final String TEAMMATE = "dev_bob";
    private static final String TEAM_NAME = "hitt_team";
    private static final String LEADER = "team_leader";

    @TempDir
    private Path tempDir;

    @AfterEach
    void resetSessionContext() {
        AgentTeamsContext.resetSessionId(null);
    }

    @Test
    void sendToNoneDrivesAvatar() {
        FakeAgent avatar = new FakeAgent();
        HumanAgentInbox inbox = new HumanAgentInbox(hittTeam(), new FakeMessageManager(), memberName -> completed(avatar));

        DeliverResult result = inbox.send("read design.md and summarise it").toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(result.messageId()).isNull();
        assertThat(avatar.deliveredBodies).containsExactly("read design.md and summarise it");
    }

    @Test
    void sendInboxDoesNotParseAtInBody() {
        FakeAgent avatar = new FakeAgent();
        HumanAgentInbox inbox = new HumanAgentInbox(hittTeam(), new FakeMessageManager(), memberName -> completed(avatar));

        DeliverResult result = inbox.send("@" + TEAMMATE + " ping me when done").toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(result.messageId()).isNull();
        assertThat(avatar.deliveredBodies).containsExactly("@" + TEAMMATE + " ping me when done");
    }

    @Test
    void sendToMemberRoutesDirect() {
        FakeMessageManager messages = new FakeMessageManager();
        HumanAgentInbox inbox = new HumanAgentInbox(hittTeam(), messages);

        DeliverResult result = inbox.send("ping me when done", TEAMMATE).toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(result.messageId()).isNotNull();
        assertThat(messages.directMessages).containsExactly(new MessageRecord("ping me when done", TEAMMATE, HUMAN));
    }

    @Test
    void sendToAllBroadcastsAsHumanAgent() {
        FakeMessageManager messages = new FakeMessageManager();
        HumanAgentInbox inbox = new HumanAgentInbox(hittTeam(), messages);

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
        FakeAgent avatar = new FakeAgent();
        HumanAgentInbox inbox = new HumanAgentInbox(hittTeam(), new FakeMessageManager(), memberName -> completed(avatar));

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
    void registerHumanAgentInboundUnknownMemberRaises() {
        TeamBackend backend = backendWithHumanAgent();

        assertThatThrownBy(() -> backend.registerHumanAgentInbound("ghost", event -> completed(null))
                .toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("'ghost' is not a registered human-agent member; registered members: [human_alice]");
    }

    @Test
    void registerHumanAgentInboundClear() {
        TeamBackend backend = backendWithHumanAgent();
        TeamBackend.HumanAgentInboundCallback callback = event -> completed(null);

        backend.registerHumanAgentInbound(HUMAN, callback).toCompletableFuture().join();
        assertThat(backend.getHumanAgentInbound(HUMAN)).isSameAs(callback);

        backend.registerHumanAgentInbound(HUMAN, null).toCompletableFuture().join();
        assertThat(backend.getHumanAgentInbound(HUMAN)).isNull();
    }

    private TeamBackend backendWithHumanAgent() {
        AgentTeamsContext.setSessionId("inbox_session");
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        TeamBackend backend = new TeamBackend(
                TEAM_NAME,
                LEADER,
                true,
                database,
                new NoOpMessager(),
                MemberMode.BUILD_MODE,
                List.of(humanSpec(HUMAN), teammateSpec(TEAMMATE)),
                null,
                null,
                true,
                false,
                List.of(),
                null,
                null,
                tempDir,
                "human-inbox-plan",
                null
        );
        backend.buildTeam(TEAM_NAME, "goal", "HITT", "leader persona").toCompletableFuture().join();
        return backend;
    }

    private static FakeTeamBackend hittTeam() {
        return new FakeTeamBackend(List.of(HUMAN), List.of(TEAMMATE, HUMAN));
    }

    private static TeamMemberSpec humanSpec(String name) {
        return new TeamMemberSpec(name, name, TeamRole.HUMAN_AGENT, "user avatar");
    }

    private static TeamMemberSpec teammateSpec(String name) {
        return new TeamMemberSpec(name, name, TeamRole.TEAMMATE, "teammate");
    }

    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private record MessageRecord(String content, String toMemberName, String fromMemberName) {
    }

    private static final class FakeTeamBackend implements TeamBackendView {
        private final List<String> humans;
        private final List<String> members;

        private FakeTeamBackend(List<String> humans, List<String> members) {
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
            return completed(null);
        }
    }

    private static final class NoOpMessager implements Messager {

        @Override
        public CompletionStage<Void> start() {
            return completed(null);
        }

        @Override
        public CompletionStage<Void> stop() {
            return completed(null);
        }

        @Override
        public CompletionStage<Void> publish(String topicId, EventMessage message) {
            return completed(null);
        }

        @Override
        public CompletionStage<Void> subscribe(String topicId, MessagerHandler handler) {
            return completed(null);
        }

        @Override
        public CompletionStage<Void> unsubscribe(String topicId) {
            return completed(null);
        }

        @Override
        public CompletionStage<Void> send(String agentId, EventMessage message) {
            return completed(null);
        }

        @Override
        public CompletionStage<Void> registerDirectMessageHandler(MessagerHandler handler) {
            return completed(null);
        }

        @Override
        public CompletionStage<Void> unregisterDirectMessageHandler() {
            return completed(null);
        }
    }
}
