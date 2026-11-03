package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.interaction.UnknownHumanAgentError;
import com.openjiuwen.agentteams.messager.InProcessMessager;
import com.openjiuwen.agentteams.messager.MessagerTransportConfig;
import com.openjiuwen.agentteams.factory.TeamFactory;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamTopic;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamLifecycle;
import com.openjiuwen.agentteams.spawn.SpawnContext;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.agentteams.tools.TeamMessageManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoordinationManagerCompatibilityTest {

    @AfterEach
    void cleanup() {
        InProcessMessager.cleanupInprocessBus();
        // TeamBackend pins a process-global shared memory DB
        // (TeamBackend.sharedMemoryDb). Without resetting it, messages from
        // one test leak into the next via listAllMessages().
        TeamBackend.resetSharedDbCache();
    }

    @Test
    void handoffUserInputShouldRouteMentionDirectlyToExistingMember() {
        TeamBackend backend = backendWithMembers(
                TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build(),
                TeamMemberSpec.builder().name("dev-1").role(TeamRole.MEMBER).build()
        );
        TeamMessageManager messageManager = backend.getMessageManager();
        List<String> leaderInbox = new ArrayList<>();
        CoordinationManager manager = new CoordinationManager(backend, messageManager, leaderInbox::add);

        CoordinationManager.UserInputHandoff handoff = manager.handoffUserInput(
                "@dev-1 please start task 123",
                TeamConstants.DEFAULT_LEADER_MEMBER_NAME
        );

        assertThat(handoff.route()).isEqualTo("direct");
        assertThat(handoff.target()).isEqualTo("dev-1");
        assertThat(handoff.deliveredContent()).isEqualTo("please start task 123");
        assertThat(handoff.messageId()).isNotBlank();
        assertThat(leaderInbox).isEmpty();
        assertThat(messageManager.getMessages("dev-1", false))
                .singleElement()
                .extracting(message -> message.getFromMemberName(), message -> message.getContent())
                .containsExactly(TeamConstants.USER_PSEUDO_MEMBER_NAME, "please start task 123");
    }

    @Test
    void handoffUserInputShouldFallbackToLeaderWhenMentionTargetMissing() {
        TeamBackend backend = backendWithMembers(
                TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build(),
                TeamMemberSpec.builder().name("dev-1").role(TeamRole.MEMBER).build()
        );
        List<String> leaderInbox = new ArrayList<>();
        CoordinationManager manager = new CoordinationManager(backend, backend.getMessageManager(), leaderInbox::add);

        CoordinationManager.UserInputHandoff handoff = manager.handoffUserInput(
                "@ghost investigate this",
                TeamConstants.DEFAULT_LEADER_MEMBER_NAME
        );

        assertThat(handoff.route()).isEqualTo("leader");
        assertThat(handoff.target()).isEqualTo(TeamConstants.DEFAULT_LEADER_MEMBER_NAME);
        assertThat(handoff.deliveredContent()).isEqualTo("@ghost investigate this");
        assertThat(handoff.messageId()).isNull();
        assertThat(leaderInbox).containsExactly("@ghost investigate this");
        assertThat(backend.getMessageManager().listAllMessages()).isEmpty();
    }

    @Test
    void handoffUserInputShouldFallbackToLeaderForPlainText() {
        TeamBackend backend = backendWithMembers(
                TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build(),
                TeamMemberSpec.builder().name("dev-1").role(TeamRole.MEMBER).build()
        );
        List<String> leaderInbox = new ArrayList<>();
        CoordinationManager manager = new CoordinationManager(backend, backend.getMessageManager(), leaderInbox::add);

        CoordinationManager.UserInputHandoff handoff = manager.handoffUserInput(
                "please review the plan",
                TeamConstants.DEFAULT_LEADER_MEMBER_NAME
        );

        assertThat(handoff.route()).isEqualTo("leader");
        assertThat(handoff.target()).isEqualTo(TeamConstants.DEFAULT_LEADER_MEMBER_NAME);
        assertThat(handoff.deliveredContent()).isEqualTo("please review the plan");
        assertThat(handoff.messageId()).isNull();
        assertThat(leaderInbox).containsExactly("please review the plan");
    }

    @Test
    void handoffUserInputShouldAllowReservedMentionTargetWhenRosterContainsIt() {
        TeamBackend backend = backendWithMembers(
                TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build(),
                TeamMemberSpec.builder().name(TeamConstants.HUMAN_AGENT_MEMBER_NAME).role(TeamRole.HUMAN_AGENT).build()
        );
        CoordinationManager manager = new CoordinationManager(backend, backend.getMessageManager(), ignored -> {
        });

        CoordinationManager.UserInputHandoff handoff = manager.handoffUserInput(
                "@human_agent you decide",
                TeamConstants.DEFAULT_LEADER_MEMBER_NAME
        );

        assertThat(handoff.route()).isEqualTo("direct");
        assertThat(handoff.target()).isEqualTo(TeamConstants.HUMAN_AGENT_MEMBER_NAME);
        assertThat(handoff.deliveredContent()).isEqualTo("you decide");
        assertThat(backend.getMessageManager().getMessages(TeamConstants.HUMAN_AGENT_MEMBER_NAME, false))
                .singleElement()
                .extracting(message -> message.getFromMemberName(), message -> message.getContent())
                .containsExactly(TeamConstants.USER_PSEUDO_MEMBER_NAME, "you decide");
    }

    @Test
    void coordinationManagerShouldSendUserBroadcastAndHumanAgentMessages() {
        TeamBackend backend = backendWithMembers(
                TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build(),
                TeamMemberSpec.builder().name("human_designer").role(TeamRole.HUMAN_AGENT).build(),
                TeamMemberSpec.builder().name("human_pm").role(TeamRole.HUMAN_AGENT).build(),
                TeamMemberSpec.builder().name("dev-1").role(TeamRole.MEMBER).build()
        );
        CoordinationManager manager = new CoordinationManager(backend, backend.getMessageManager(), ignored -> {
        });

        String broadcastId = manager.broadcastFromUser("everyone read this");
        String directHumanId = manager.handoffHumanAgentInput("on it", TeamConstants.DEFAULT_LEADER_MEMBER_NAME, "human_pm");
        String defaultHumanId = manager.handoffHumanAgentInput("looking", "dev-1", null);

        assertThat(broadcastId).isNotBlank();
        assertThat(directHumanId).isNotBlank();
        assertThat(defaultHumanId).isNotBlank();
        assertThat(backend.getMessageManager().getBroadcastMessages(false))
                .singleElement()
                .extracting(message -> message.getFromMemberName(), message -> message.getContent())
                .containsExactly(TeamConstants.USER_PSEUDO_MEMBER_NAME, "everyone read this");
        assertThat(backend.getMessageManager().getMessages(TeamConstants.DEFAULT_LEADER_MEMBER_NAME, false))
                .singleElement()
                .extracting(message -> message.getFromMemberName(), message -> message.getContent())
                .containsExactly("human_pm", "on it");
        assertThat(backend.getMessageManager().getMessages("dev-1", false))
                .singleElement()
                .extracting(message -> message.getFromMemberName(), message -> message.getContent())
                .containsExactly("human_designer", "looking");
    }

    @Test
    void coordinationManagerShouldRejectUnknownHumanAgentSender() {
        TeamBackend backend = backendWithMembers(
                TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build(),
                TeamMemberSpec.builder().name("human_designer").role(TeamRole.HUMAN_AGENT).build()
        );
        CoordinationManager manager = new CoordinationManager(backend, backend.getMessageManager(), ignored -> {
        });

        assertThatThrownBy(() -> manager.handoffHumanAgentInput("spoof", TeamConstants.DEFAULT_LEADER_MEMBER_NAME, "ghost"))
                .isInstanceOf(UnknownHumanAgentError.class)
                .hasMessageContaining("registered human-agent member");
    }

    @Test
    void startShouldWireTransportAndFilterSelfPublishedEventsLikePythonCoordinationManager() throws Exception {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder()
                .name("coordination-lifecycle")
                .members(List.of(
                        TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("dev-1").role(TeamRole.MEMBER).build()))
                .build());
        AtomicReference<EventMessage> observed = new AtomicReference<>();
        AtomicInteger listenerCount = new AtomicInteger();
        agent.addEventListener((java.util.function.Consumer<EventMessage>) event -> {
            observed.set(event);
            listenerCount.incrementAndGet();
        });

        agent.getCoordinationManager().start();

        assertThat(agent.getCoordinationKernel().isRunning()).isTrue();
        // Topics are now session-scoped per Python 0.1.15 TeamTopic.build(session_id, team_name).
        // Three topics (TEAM/TASK/MESSAGE) — broadcast rides MESSAGE, no separate broadcast topic.
        String sessionId = SpawnContext.getSessionId();
        String teamTopic = TeamTopic.TEAM.build(sessionId, "coordination-lifecycle");
        assertThat(agent.getCoordinationManager().subscribedTopics())
                .containsExactly(
                        teamTopic,
                        TeamTopic.TASK.build(sessionId, "coordination-lifecycle"),
                        TeamTopic.MESSAGE.build(sessionId, "coordination-lifecycle"));
        assertThat(agent.getContext().getLifecycle()).isEqualTo(TeamLifecycle.RUNNING);
        assertThat(agent.getTeamBackend().getDb().member
                .getMember(TeamConstants.DEFAULT_LEADER_MEMBER_NAME, "coordination-lifecycle")
                .getStatus()).isEqualTo(MemberStatus.READY.value());

        agent.getTeamBackend().getMessager().publish(teamTopic, EventMessage.builder()
                .senderId(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                .eventType("member_status_changed")
                .payload(Map.of("team_name", "coordination-lifecycle", "member_name", "dev-1"))
                .build()).join();
        assertThat(listenerCount).hasValue(1);
        assertThat(observed.get().getEventType()).isEqualTo("member_status_changed");
        assertThat(agent.getCoordinationKernel().isRunning()).isTrue();

        agent.getTeamBackend().getMessager().publish(teamTopic, EventMessage.builder()
                .senderId("dev-1")
                .eventType("member_status_changed")
                .payload(Map.of("team_name", "coordination-lifecycle", "member_name", "dev-1"))
                .build()).join();

        Thread.sleep(100L);
        agent.getCoordinationManager().stop();
        assertThat(listenerCount).hasValue(2);
        assertThat(agent.getCoordinationManager().subscribedTopics()).isEmpty();
        assertThat(agent.getCoordinationKernel().isRunning()).isFalse();
        assertThat(agent.getContext().getLifecycle()).isEqualTo(TeamLifecycle.COMPLETED);
    }

    @Test
    void pauseShouldPublishTeamStandbyAndUnsubscribeTransportLikePythonPauseCoordination() throws Exception {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder()
                .name("coordination-pause")
                .members(List.of(TeamMemberSpec.builder()
                        .name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                        .role(TeamRole.LEADER)
                        .build()))
                .build());
        InProcessMessager observer = new InProcessMessager(MessagerTransportConfig.builder()
                .teamName("coordination-pause")
                .nodeId("observer")
                .build());
        AtomicReference<EventMessage> standby = new AtomicReference<>();
        // Standby publishes on the session-scoped TEAM topic (Python kernel.pause path).
        String pauseSessionId = SpawnContext.getSessionId();
        String pauseTeamTopic = TeamTopic.TEAM.build(pauseSessionId, "coordination-pause");
        observer.subscribe(pauseTeamTopic, message -> {
            if ("team_standby".equals(message.getEventType())) {
                standby.set(message);
            }
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }).join();

        agent.getCoordinationManager().start();
        agent.getCoordinationManager().pause();

        assertThat(standby.get()).isNotNull();
        assertThat(standby.get().getPayload()).containsEntry("team_name", "coordination-pause");
        assertThat(agent.getContext().getLifecycle()).isEqualTo(TeamLifecycle.PAUSED);
        assertThat(agent.getCoordinationKernel().isRunning()).isFalse();
        assertThat(agent.getCoordinationManager().subscribedTopics()).isEmpty();
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25L);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private TeamBackend backendWithMembers(TeamMemberSpec... memberSpecs) {
        TeamBackend backend = new TeamBackend(
                "coordination-team",
                TeamConstants.DEFAULT_LEADER_MEMBER_NAME,
                true,
                new InProcessMessager(MessagerTransportConfig.builder()
                        .teamName("coordination-team")
                        .nodeId(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                        .build())
        );
        backend.syncMembers(List.of(memberSpecs));
        return backend;
    }
}
