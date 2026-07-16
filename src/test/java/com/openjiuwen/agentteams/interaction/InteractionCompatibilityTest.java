
package com.openjiuwen.agentteams.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.agentteams.tools.TeamMessage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class InteractionCompatibilityTest {
    @Test
    void routerShouldParseMentionAndReservedNamesLikePython() {
        assertThat(Router.parseMention("@dev-1 please start task 123"))
                .contains(new MentionRoute("dev-1", "please start task 123"));
        assertThat(Router.parseMention("just a regular message")).isEmpty();
        assertThat(Router.parseMention("")).isEmpty();
        assertThat(Router.parseMention("@dev-1")).isEmpty();
        assertThat(Router.parseMention("@human_agent you decide"))
                .contains(new MentionRoute("human_agent", "you decide"));
        assertThat(Router.isReservedName("user")).isTrue();
        assertThat(Router.isReservedName("team_leader")).isTrue();
        assertThat(Router.isReservedName("human_agent")).isTrue();
        assertThat(Router.isReservedName("backend-dev-1")).isFalse();
    }

    @Test
    void userInboxShouldRouteDirectAndBroadcastAsUser() {
        TeamBackend backend = backend("interaction-team", false, List.of());
        UserInbox inbox = new UserInbox(backend.getMessageManager());

        String directId = inbox.direct("alice", "look at this").join();
        String broadcastId = inbox.broadcast("everyone read this").join();

        assertThat(directId).isNotBlank();
        assertThat(broadcastId).isNotBlank();
        assertThat(backend.getMessageManager().getMessages("alice", false)).singleElement()
                .extracting(TeamMessage::getFromMemberName).isEqualTo(TeamConstants.USER_PSEUDO_MEMBER_NAME);
        assertThat(backend.getMessageManager().getBroadcastMessages(false)).anySatisfy(
                message -> assertThat(message.getFromMemberName()).isEqualTo(TeamConstants.USER_PSEUDO_MEMBER_NAME));
    }

    @Test
    void humanAgentInboxShouldRespectEnabledStateAndSenderSelection() {
        TeamBackend noHitt = backend("no-hitt", false, List.of());
        HumanAgentInbox disabledInbox = new HumanAgentInbox(noHitt, noHitt.getMessageManager());
        assertThatThrownBy(() -> disabledInbox.send("hi", null, null)).isInstanceOf(HumanAgentNotEnabledError.class);

        TeamBackend backend = backend("multi-hitt", true,
                List.of(humanMember("human_designer", "Visual designer"), humanMember("human_pm", "Product manager")));
        HumanAgentInbox inbox = new HumanAgentInbox(backend, backend.getMessageManager());

        assertThatThrownBy(() -> inbox.send("spoofing", "team_leader", "ghost"))
                .isInstanceOf(UnknownHumanAgentError.class);

        String msgId = inbox.send("ok", "team_leader", "human_pm").join();
        assertThat(msgId).isNotBlank();
        assertThat(backend.getMessageManager().getMessages("team_leader", false))
                .anySatisfy(message -> assertThat(message.getFromMemberName()).isEqualTo("human_pm"));
    }

    @Test
    void humanAgentInboxShouldDefaultToReservedHumanAgentWhenPresent() {
        TeamBackend backend = backend("default-human", true,
                List.of(humanMember(TeamConstants.HUMAN_AGENT_MEMBER_NAME, "Human"), humanMember("human_pm", "PM")));
        HumanAgentInbox inbox = new HumanAgentInbox(backend, backend.getMessageManager());

        String msgId = inbox.send("on it", "team_leader", null).join();
        assertThat(msgId).isNotBlank();
        assertThat(backend.getMessageManager().getMessages("team_leader", false)).anySatisfy(
                message -> assertThat(message.getFromMemberName()).isEqualTo(TeamConstants.HUMAN_AGENT_MEMBER_NAME));
    }

    @Test
    void userInboxDeliverToLeaderShouldForwardVerbatim() {
        AtomicReference<String> delivered = new AtomicReference<>();

        UserInbox.deliverToLeader(delivered::set, "plan the release");

        assertThat(delivered.get()).isEqualTo("plan the release");
    }

    private static TeamBackend backend(String teamName, boolean hitt, List<TeamMemberSpec> members) {
        TeamBackend backend =
            new TeamBackend(teamName, TeamConstants.DEFAULT_LEADER_MEMBER_NAME, true, new NoopMessager());
        List<TeamMemberSpec> allMembers = new java.util.ArrayList<>(members);
        if (hitt) {
            allMembers.add(0, humanMember(TeamConstants.HUMAN_AGENT_MEMBER_NAME, "Human"));
        }
        backend.syncMembers(allMembers);
        return backend;
    }

    private static TeamMemberSpec humanMember(String name, String description) {
        return TeamMemberSpec.builder().name(name).role(TeamRole.HUMAN_AGENT).description(description).build();
    }

    private static final class NoopMessager implements Messager {
        @Override
        public java.util.concurrent.CompletableFuture<Void> start() {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> stop() {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> publish(String topicId, EventMessage message) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> subscribe(String topicId,
                com.openjiuwen.agentteams.messager.MessagerHandler handler) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> unsubscribe(String topicId) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> send(String agentId, EventMessage message) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> registerDirectMessageHandler(
                com.openjiuwen.agentteams.messager.MessagerHandler handler) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> unregisterDirectMessageHandler() {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
}
