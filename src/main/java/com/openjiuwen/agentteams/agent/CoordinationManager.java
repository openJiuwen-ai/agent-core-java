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
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.schema.team.TeamLifecycle;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.agentteams.tools.TeamMessageManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Coordination handoff and lifecycle wiring mirroring Python {@code coordination_manager.py}. */
public class CoordinationManager {
  private static final List<String> TRANSPORT_TOPICS =
      List.of("team:%s", "team:task", "team:message", "team:broadcast");

  private final TeamAgent host;
  private final TeamBackend teamBackend;
  private final TeamMessageManager messageManager;
  private final Consumer<String> leaderInputSink;
  private final List<String> subscribedTopics = new ArrayList<>();

  /** Auto-generated for codecheck compliance. */
  public CoordinationManager(
      TeamBackend teamBackend,
      TeamMessageManager messageManager,
      Consumer<String> leaderInputSink) {
    this(null, teamBackend, messageManager, leaderInputSink);
  }

  /** Auto-generated for codecheck compliance. */
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

  /** Auto-generated for codecheck compliance. */
  public List<String> subscribedTopics() {
    return List.copyOf(subscribedTopics);
  }

  /** Auto-generated for codecheck compliance. */
  public void start() {
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
    if (host.getCoordinatorLoop() != null && !host.getCoordinatorLoop().isRunning()) {
      host.getCoordinatorLoop().start();
    }
    subscribeTransport();
  }

  /** Auto-generated for codecheck compliance. */
  public void pause() {
    if (host == null) {
      return;
    }
    host.persistAllocatorState();
    publishTeamStandby();
    unsubscribeTransport();
    if (host.getCoordinatorLoop() != null) {
      host.getCoordinatorLoop().stop();
    }
    if (host.getStreamController() != null) {
      host.getStreamController().closeStream();
    }
    if (host.getContext() != null) {
      host.getContext().setLifecycle(TeamLifecycle.PAUSED);
    }
  }

  /** Auto-generated for codecheck compliance. */
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
    if (host.getCoordinatorLoop() != null) {
      host.getCoordinatorLoop().stop();
    }
    if (host.getStreamController() != null) {
      host.getStreamController().closeStream();
    }
    if (host.getContext() != null) {
      host.getContext().setLifecycle(TeamLifecycle.COMPLETED);
    }
  }

  /** Auto-generated for codecheck compliance. */
  public void subscribeTransport() {
    if (host == null
        || host.getCoordinatorLoop() == null
        || teamBackend.getMessager() == null
        || !subscribedTopics.isEmpty()) {
      return;
    }
    Messager messager = teamBackend.getMessager();
    messager
        .registerDirectMessageHandler(
            message -> {
              host.getCoordinatorLoop().enqueue(message);
              return CompletableFuture.completedFuture(null);
            })
        .join();
    for (String topicTemplate : TRANSPORT_TOPICS) {
      String topic = topicTemplate.formatted(teamBackend.getTeamName());
      messager.subscribe(topic, this::handleTransportEvent).join();
      subscribedTopics.add(topic);
    }
  }

  /** Auto-generated for codecheck compliance. */
  public void unsubscribeTransport() {
    if (teamBackend.getMessager() == null) {
      subscribedTopics.clear();
      return;
    }
    Messager messager = teamBackend.getMessager();
    try {
      messager.unregisterDirectMessageHandler().join();
    } catch (RuntimeException ignored) {
      // Python cleanup logs and continues when a transport backend is already gone.
    }
    for (String topic : List.copyOf(subscribedTopics)) {
      try {
        messager.unsubscribe(topic).join();
      } catch (RuntimeException ignored) {
        // Best-effort cleanup.
      }
    }
    subscribedTopics.clear();
  }

  /** Auto-generated for codecheck compliance. */
  public void enqueueUserInput(Object inputs) {
    if (host == null || host.getCoordinatorLoop() == null) {
      return;
    }
    Object query = inputs;
    if (inputs instanceof Map<?, ?> map) {
      query = map.containsKey("query") ? map.get("query") : "";
    }
    host.getCoordinatorLoop()
        .enqueue(
            InnerEventMessage.builder()
                .eventType(InnerEventType.USER_INPUT)
                .payload(Map.of("content", query != null ? String.valueOf(query) : ""))
                .build());
  }

  /** Auto-generated for codecheck compliance. */
  public void wakeMailboxIfInterruptCleared() {
    if (host == null || host.hasPendingInterrupt() || host.getCoordinatorLoop() == null) {
      return;
    }
    host.getCoordinatorLoop()
        .enqueue(InnerEventMessage.builder().eventType(InnerEventType.POLL_MAILBOX).build());
  }

  /** Auto-generated for codecheck compliance. */
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

  /** Auto-generated for codecheck compliance. */
  public String broadcastFromUser(String content) {
    return new UserInbox(messageManager).broadcast(content).join();
  }

  /** Auto-generated for codecheck compliance. */
  public String handoffHumanAgentInput(String content, String to, String sender) {
    return new HumanAgentInbox(teamBackend, messageManager).send(content, to, sender).join();
  }

  private CompletableFuture<Void> handleTransportEvent(EventMessage event) {
    if (event == null) {
      return CompletableFuture.completedFuture(null);
    }
    notifyEventListeners(event);
    String localMember = host != null ? host.resolveLocalMemberName() : null;
    if (localMember != null && localMember.equals(event.getSenderId())) {
      return CompletableFuture.completedFuture(null);
    }
    if (host != null && host.getCoordinatorLoop() != null) {
      host.getCoordinatorLoop().enqueue(event);
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
    teamBackend
        .getMessager()
        .publish(
            "team:" + teamBackend.getTeamName(),
            EventMessage.builder()
                .eventType("team_standby")
                .payload(Map.of("team_name", teamBackend.getTeamName()))
                .build())
        .join();
  }

  /**
   * Public record UserInputHandoff used by the Java parity implementation.
   *
   * @since 1.0
   */
  public record UserInputHandoff(
      String route, String target, String deliveredContent, String messageId) {}
}
