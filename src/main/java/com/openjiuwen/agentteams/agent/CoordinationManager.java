/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.interaction.HumanAgentInbox;
import com.openjiuwen.agentteams.interaction.MentionRoute;
import com.openjiuwen.agentteams.interaction.Router;
import com.openjiuwen.agentteams.interaction.UserInbox;
import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamEvent;
import com.openjiuwen.agentteams.schema.events.TeamTopic;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.schema.team.TeamLifecycle;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.agentteams.tools.TeamMessageManager;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Coordination handoff and lifecycle wiring mirroring Python {@code coordination_manager.py}.
 *
 * @since 2026/7/9
 */
public class CoordinationManager {
    private final TeamAgent host;
    private final TeamBackend teamBackend;
    private final TeamMessageManager messageManager;
    private final Consumer<String> leaderInputSink;
    private final List<String> subscribedTopics = new ArrayList<>();

    /**
     * Construct a manager without a host agent (delegates to the host-bearing overload with {@code null}).
     *
     * @param teamBackend the team backend used for messager and member lookups
     * @param messageManager the manager used to deliver user-driven messages
     * @param leaderInputSink the sink that receives plain leader-directed user input
     * @since 0.1.7
     */
    public CoordinationManager(
            TeamBackend teamBackend,
            TeamMessageManager messageManager,
            Consumer<String> leaderInputSink) {
        this(null, teamBackend, messageManager, leaderInputSink);
    }

    /**
     * Construct a manager bound to a host {@link TeamAgent}.
     *
     * @param host the host agent this manager coordinates for; may be {@code null} in helper-only usage
     * @param teamBackend the team backend used for messager and member lookups
     * @param messageManager the manager used to deliver user-driven messages
     * @param leaderInputSink the sink that receives plain leader-directed user input
     * @since 0.1.7
     */
    public CoordinationManager(
            TeamAgent host,
            TeamBackend teamBackend,
            TeamMessageManager messageManager,
            Consumer<String> leaderInputSink) {
        this.host = host;
        this.teamBackend = teamBackend;
        this.messageManager = messageManager;
        this.leaderInputSink = leaderInputSink;
    }

    /**
     * Return the team topics this manager has currently subscribed to.
     *
     * @return an immutable copy of the subscribed topic strings
     * @since 0.1.7
     */
    public List<String> subscribedTopics() {
        return List.copyOf(subscribedTopics);
    }

    /**
     * Start coordination for the host agent: mark the member ready,
     * start the kernel, and subscribe to transport topics.
     *
     * @since 0.1.7
     */
    public void start() {
        String who = host != null ? host.resolveLocalMemberName() : "null";
        Loggers.AGENT.info("CoordinationManager.start() called for member={} role={}",
                who, host != null && host.getContext() != null ? host.getContext().getRole() : "?");
        if (host == null) {
            return;
        }
        if (host.getContext() != null) {
            host.getContext().setLifecycle(TeamLifecycle.RUNNING);
        }
        if (host.getContext() != null
                && host.getContext().getRole() == com.openjiuwen.agentteams.schema.team.TeamRole.LEADER) {
            host.persistLeaderConfigToSession();
            host.recoverTeam();
        }
        teamBackend.updateMemberStatus(host.resolveLocalMemberName(), MemberStatus.READY);
        if (host.getCoordinationKernel() != null && !host.getCoordinationKernel().isRunning()) {
            host.getCoordinationKernel().start();
        }
        subscribeTransport();
    }

    /**
     * Pause coordination: persist allocator state, publish a standby event, unsubscribe transport, and stop the kernel.
     *
     * @since 0.1.7
     */
    public void pause() {
        if (host == null) {
            return;
        }
        host.persistAllocatorState();
        publishTeamStandby();
        unsubscribeTransport();
        if (host.getCoordinationKernel() != null) {
            host.getCoordinationKernel().stop();
        }
        if (host.getStreamController() != null) {
            host.getStreamController().closeStream();
        }
        if (host.getContext() != null) {
            host.getContext().setLifecycle(TeamLifecycle.PAUSED);
        }
    }

    /**
     * Stop coordination: persist allocator state, unsubscribe transport,
     * shut down spawned members, and stop the kernel.
     *
     * @since 0.1.7
     */
    public void stop() {
        if (host == null) {
            return;
        }
        host.persistAllocatorState();
        unsubscribeTransport();
        if (host.getSpawnManager() != null) {
            host.getSpawnManager().cancelRecoveryTasks();
            host.getSpawnManager().shutdownAllHandles();
        }
        if (host.getMemoryManager() != null) {
            host.getMemoryManager().close();
        }
        if (host.getCoordinationKernel() != null) {
            host.getCoordinationKernel().stop();
        }
        if (host.getStreamController() != null) {
            host.getStreamController().closeStream();
        }
        if (host.getContext() != null) {
            host.getContext().setLifecycle(TeamLifecycle.COMPLETED);
        }
    }

    /**
     * Subscribe messager to the three team topics (TEAM / TASK / MESSAGE) plus the
     * direct-message handler, mirroring Python {@code kernel.subscribe_transport}:
     * iterate {@code TeamTopic} and call {@code topic.build(session_id, team_name)}.
     * Broadcast rides the MESSAGE topic (no separate broadcast subscription).
     */
    public void subscribeTransport() {
        String who = host != null ? host.resolveLocalMemberName() : "null";
        if (host == null) {
            Loggers.AGENT.info("CoordinationManager.subscribeTransport: SKIP host=null for {}", who);
            return;
        }
        if (host.getCoordinationKernel() == null) {
            Loggers.AGENT.info("CoordinationManager.subscribeTransport: SKIP coordinationKernel=null for {}", who);
            return;
        }
        if (teamBackend.getMessager() == null) {
            Loggers.AGENT.info("CoordinationManager.subscribeTransport: SKIP messager=null for {}", who);
            return;
        }
        if (!subscribedTopics.isEmpty()) {
            Loggers.AGENT.info("CoordinationManager.subscribeTransport: SKIP already subscribed for {}", who);
            return;
        }
        Loggers.AGENT.info("CoordinationManager.subscribeTransport: subscribing for member={}", who);
        Messager messager = teamBackend.getMessager();
        messager
                .registerDirectMessageHandler(
                        message -> {
                            host.getCoordinationKernel().enqueue(message);
                            return CompletableFuture.completedFuture(null);
                        })
                .join();
        String sessionId = teamBackend.getTeamSessionId();
        String teamName = teamBackend.getTeamName();
        for (TeamTopic topic : TeamTopic.values()) {
            String topicStr = topic.build(sessionId, teamName);
            messager.subscribe(topicStr, this::handleTransportEvent).join();
            subscribedTopics.add(topicStr);
        }
    }

    /**
     * Unsubscribe the messager from the direct-message handler and all previously subscribed team topics.
     *
     * @since 0.1.7
     */
    public void unsubscribeTransport() {
        if (teamBackend.getMessager() == null) {
            subscribedTopics.clear();
            return;
        }
        Messager messager = teamBackend.getMessager();
        try {
            messager.unregisterDirectMessageHandler().join();
        } catch (CompletionException ignored) {
            // Python cleanup logs and continues when a transport backend is already gone.
        }
        for (String topic : List.copyOf(subscribedTopics)) {
            try {
                messager.unsubscribe(topic).join();
            } catch (CompletionException ignored) {
                // Best-effort cleanup.
            }
        }
        subscribedTopics.clear();
    }

    /**
     * Enqueue user input onto the host's coordination kernel, unwrapping a {@code query} field when the input is a map.
     *
     * @param inputs the user input; either a raw string or a map containing a {@code query} key
     * @since 0.1.7
     */
    public void enqueueUserInput(Object inputs) {
        if (host == null || host.getCoordinationKernel() == null) {
            return;
        }
        Object query = inputs;
        if (inputs instanceof Map<?, ?> map) {
            query = map.containsKey("query") ? map.get("query") : "";
        }
        host.getCoordinationKernel().enqueueUserInput(query != null ? String.valueOf(query) : "");
    }

    /**
     * Wake the kernel's mailbox poll once the host no longer has a pending interrupt.
     *
     * @since 0.1.7
     */
    public void wakeMailboxIfInterruptCleared() {
        if (host == null || host.hasPendingInterrupt() || host.getCoordinationKernel() == null) {
            return;
        }
        host.getCoordinationKernel().enqueuePollMailbox();
    }

    /**
     * Route a user input string to a mentioned teammate or, lacking a mention, deliver it to the leader sink.
     *
     * @param query the raw user input, possibly prefixed with a {@code @member} mention
     * @param leaderMemberName the leader member name used when the input is routed to the leader
     * @return the {@link UserInputHandoff} describing where the input was delivered
     * @since 0.1.7
     */
    public UserInputHandoff handoffUserInput(String query, String leaderMemberName) {
        Optional<MentionRoute> mention = Router.parseMention(query);
        if (mention.isPresent() && teamBackend.hasMember(mention.get().target())) {
            String target = mention.get().target();
            String body = mention.get().body();
            String messageId = new UserInbox(messageManager).direct(target, body).join();
            return new UserInputHandoff("direct", target, body, messageId);
        }
        UserInbox.deliverToLeader(leaderInputSink, query);
        return new UserInputHandoff("leader", leaderMemberName, query, null);
    }

    /**
     * Broadcast a user-supplied message to every team member.
     *
     * @param content the message body to broadcast
     * @return the message id assigned to the broadcast
     * @since 0.1.7
     */
    public String broadcastFromUser(String content) {
        return new UserInbox(messageManager).broadcast(content).join();
    }

    /**
     * Send a message on behalf of the human agent member to a named recipient.
     *
     * @param content the message body
     * @param to the target member name
     * @param sender the sender member name, typically the human agent
     * @return the message id assigned to the delivered message
     * @since 0.1.7
     */
    public String handoffHumanAgentInput(String content, String to, String sender) {
        return new HumanAgentInbox(teamBackend, messageManager).send(content, to, sender).join();
    }

    private CompletableFuture<Void> handleTransportEvent(EventMessage event) {
        if (event == null) {
            return CompletableFuture.completedFuture(null);
        }
        notifyEventListeners(event);
        String localMember = host != null ? host.resolveLocalMemberName() : null;
        String eventType = event.getEventType();

        // Skip events published by this member (echo suppression).
        // Since each member now has a unique nodeId (= memberName), this correctly
        // filters only the member's own events while letting everything else through.
        if (localMember != null && localMember.equals(event.getSenderId())) {
            return CompletableFuture.completedFuture(null);
        }
        if (host != null && host.getCoordinationKernel() != null) {
            // Log all transport events for visibility — previously only logged task_* and
            // broadcast, but "message" events (from send_message) are critical for leader
            // to receive analyst reports.
            Loggers.AGENT.info(
                    "CoordinationManager: enqueuing event type={} senderId={} for member={}",
                    eventType, event.getSenderId(), localMember);
            host.getCoordinationKernel().enqueue(event);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void notifyEventListeners(EventMessage event) {
        if (host == null) {
            return;
        }
        for (Object listener : host.eventListeners()) {
            if (listener instanceof java.util.function.Consumer<?> consumer) {
                @SuppressWarnings("unchecked")
                java.util.function.Consumer<EventMessage> eventConsumer =
                        (java.util.function.Consumer<EventMessage>) consumer;
                eventConsumer.accept(event);
            }
        }
    }

    private void publishTeamStandby() {
        if (host == null
                || host.getContext() == null
                || host.getContext().getRole() != com.openjiuwen.agentteams.schema.team.TeamRole.LEADER) {
            return;
        }

        // Mirrors Python kernel.pause: TeamTopic.TEAM.build(get_session_id(), team_name).
        // Use the team-level session id pinned on the backend so the event reaches members
        // subscribed to the team session — not the leader's transient ReAct-stream session.
        teamBackend
                .getMessager()
                .publish(
                        TeamTopic.TEAM.build(teamBackend.getTeamSessionId(), teamBackend.getTeamName()),
                        EventMessage.builder()
                                .eventType(TeamEvent.STANDBY)
                                .payload(Map.of("team_name", teamBackend.getTeamName()))
                                .build())
                .join();
    }

    /**
     * Public record UserInputHandoff used by the Java parity implementation.
     *
     * @since 0.1.7
     */
    public record UserInputHandoff(
            String route, String target, String deliveredContent, String messageId) {}
}
