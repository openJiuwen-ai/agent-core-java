/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.tools.TeamMessage;
import com.openjiuwen.agentteams.tools.TeamTask;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * First Java dispatcher slice for team coordination events.
 *
 * <p>This mirrors the locally verifiable Python {@code agent/dispatcher.py} paths: inner user
 * input, mailbox polling, and simple transport control events. Stale task nudges, tool approval
 * resumes, and full task-board orchestration remain later slices.
 */
public class EventDispatcher {
  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_STANDBY = "team_standby";

  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_CLEANED = "team_cleaned";

  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_MESSAGE = "message";

  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_BROADCAST = "broadcast";

  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_TASK_CREATED = "task_created";

  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_TASK_UPDATED = "task_updated";

  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_TASK_CLAIMED = "task_claimed";

  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_TASK_COMPLETED = "task_completed";

  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_TASK_CANCELLED = "task_cancelled";

  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_TASK_UNBLOCKED = "task_unblocked";

  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_MEMBER_SPAWNED = "member_spawned";

  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_MEMBER_RESTARTED = "member_restarted";

  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_MEMBER_STATUS_CHANGED = "member_status_changed";

  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_MEMBER_EXECUTION_CHANGED = "member_execution_changed";

  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_TOOL_APPROVAL_RESULT = "tool_approval_result";

  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_MEMBER_CANCELED = "member_canceled";

  /** Auto-generated for codecheck compliance. */
  public static final String EVENT_MEMBER_SHUTDOWN = "member_shutdown";

  /** Auto-generated for codecheck compliance. */
  public static final long STALE_CLAIM_MILLIS = 10 * 60 * 1000L;

  /** Auto-generated for codecheck compliance. */
  public static final long STALE_PENDING_MILLIS = 10 * 60 * 1000L;

  private final TeamAgent host;
  @SuppressWarnings("unused")
  private volatile boolean streamingActive;
  private final Map<String, Long> lastStaleNudgeMillis = new ConcurrentHashMap<>();
  private final Map<String, Long> lastPendingNudgeMillis = new ConcurrentHashMap<>();

  /** Auto-generated for codecheck compliance. */
  public EventDispatcher(TeamAgent host) {
    this.host = Objects.requireNonNull(host, "host is required");
  }

  /** Auto-generated for codecheck compliance. */
  public void dispatch(Object event) {
    if (!host.isAgentReady()) {
      return;
    }
    if (event instanceof InnerEventMessage innerEvent) {
      handleInnerEvent(innerEvent);
      return;
    }
    if (event instanceof EventMessage transportEvent) {
      handleTransportEvent(transportEvent);
    }
  }

  /** Auto-generated for codecheck compliance. */
  public void handleInnerEvent(InnerEventMessage event) {
    if (event == null || event.getEventType() == null) {
      return;
    }
    if (event.getEventType() == InnerEventType.USER_INPUT) {
      Object content = event.getPayload() != null ? event.getPayload().get("content") : null;
      host.deliverInput(content != null ? String.valueOf(content) : "");
      return;
    }
    if (event.getEventType() == InnerEventType.POLL_MAILBOX) {
      processUnreadMessages(host.resolveLocalMemberName());
      return;
    }
    if (event.getEventType() == InnerEventType.POLL_TASK) {
      checkStaleClaimedTasks();
      checkStalePendingTasks();
    }
  }

  /** Auto-generated for codecheck compliance. */
  public void handleTransportEvent(EventMessage event) {
    String localMember = host.currentMemberName();
    if (localMember == null || localMember.isBlank()) {
      return;
    }
    String type = event.getEventType();
    if (EVENT_STANDBY.equals(type)) {
      host.pausePolls();
      return;
    }
    if (EVENT_CLEANED.equals(type)) {
      if (host.getContext().getRole() == TeamRole.LEADER) {
        return;
      }
      host.shutdownSelf();
      return;
    }
    if (EVENT_MESSAGE.equals(type) || EVENT_BROADCAST.equals(type)) {
      if (EVENT_MESSAGE.equals(type) && host.getContext().getRole() == TeamRole.LEADER) {
        ackUserBoundMessage(event);
      }
      host.resumePolls();
      processUnreadMessages(host.resolveLocalMemberName());
      return;
    }
    if (EVENT_TASK_CLAIMED.equals(type)) {
      if (handleTaskClaimed(event)) {
        return;
      }
    }
    if (isTaskEvent(type)) {
      if (host.hasInFlightRound()) {
        return;
      }
      handleTaskBoardEvent();
      return;
    }
    if (isMemberEvent(type)) {
      if (host.getContext().getRole() == TeamRole.LEADER) {
        handleLeaderMemberLifecycleEvent(event);
      } else {
        handleTeammateMemberLifecycleEvent(event);
      }
      return;
    }
    if (EVENT_TOOL_APPROVAL_RESULT.equals(type)) {
      handleToolApprovalResult(event);
    }
  }

  /** Auto-generated for codecheck compliance. */
  public boolean handleTaskClaimed(EventMessage event) {
    Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Map.of();
    String memberName = stringValue(payload.get("member_name"));
    String localMember = host.currentMemberName();
    if (memberName == null || memberName.isBlank() || !memberName.equals(localMember)) {
      return false;
    }
    host.resumePolls();
    String taskId = stringValue(payload.get("task_id"));
    host.deliverInput("Task assigned to you: " + (taskId != null ? taskId : ""));
    return true;
  }

  /** Auto-generated for codecheck compliance. */
  public List<TeamTask> handleMemberStatusChanged(EventMessage event) {
    if (host.getContext().getRole() != TeamRole.LEADER) {
      return List.of();
    }
    Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Map.of();
    String memberName = stringValue(payload.get("member_name"));
    String oldStatus = stringValue(payload.get("old_status"));
    String newStatus = stringValue(payload.get("new_status"));
    return nudgeIdleMemberWithStaleClaims(memberName, oldStatus, newStatus);
  }

  /** Auto-generated for codecheck compliance. */
  public String handleLeaderMemberLifecycleEvent(EventMessage event) {
    if (host.getContext().getRole() != TeamRole.LEADER || event == null) {
      return null;
    }
    Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Map.of();
    String memberName = stringValue(payload.get("member_name"));
    return switch (event.getEventType()) {
      case EVENT_MEMBER_SPAWNED -> "Member online: " + memberName;
      case EVENT_MEMBER_RESTARTED ->
          "Member restarted: "
              + memberName
              + " restart_count="
              + payload.getOrDefault("restart_count", 1);
      case EVENT_MEMBER_STATUS_CHANGED -> {
        String oldStatus = stringValue(payload.get("old_status"));
        String newStatus = stringValue(payload.get("new_status"));
        nudgeIdleMemberWithStaleClaims(memberName, oldStatus, newStatus);
        yield "Member status changed: " + memberName + " " + oldStatus + " -> " + newStatus;
      }
      case EVENT_MEMBER_EXECUTION_CHANGED ->
          "Member execution changed: "
              + memberName
              + " "
              + payload.get("old_status")
              + " -> "
              + payload.get("new_status");
      case EVENT_MEMBER_SHUTDOWN -> "Member shutdown: " + memberName;
      case EVENT_MEMBER_CANCELED -> "Member canceled: " + memberName;
      default -> null;
    };
  }

  /** Auto-generated for codecheck compliance. */
  public List<TeamTask> nudgeIdleMemberWithStaleClaims(
      String memberName, String oldStatus, String newStatus) {
    if (memberName == null || memberName.isBlank()) {
      return List.of();
    }
    if (!("ready".equals(newStatus) || "error".equals(newStatus))) {
      return List.of();
    }
    if (Objects.equals(oldStatus, newStatus)) {
      return List.of();
    }
    long now = System.currentTimeMillis();
    List<TeamTask> stale =
        host.getTaskManager().getTasksByAssignee(memberName, "claimed").stream()
            .filter(task -> now - task.getUpdatedAt() >= STALE_CLAIM_MILLIS)
            .toList();
    if (stale.isEmpty()) {
      return stale;
    }
    for (TeamTask task : stale) {
      lastStaleNudgeMillis.put(task.getTaskId(), now);
    }
    List<String> lines = new ArrayList<>();
    lines.add("You still have " + stale.size() + " stale claimed task(s):");
    for (TeamTask task : stale) {
      lines.add("- [" + task.getTaskId() + "] " + task.getTitle() + ": " + task.getContent());
    }
    host.getMessageManager().sendMessage(String.join("\n", lines), memberName).join();
    return stale;
  }

  /** Auto-generated for codecheck compliance. */
  public List<TeamTask> handleTaskBoardEvent() {
    host.resumePolls();
    return nudgeIdleAgent(host.resolveLocalMemberName(), false);
  }

  /** Auto-generated for codecheck compliance. */
  public List<TeamTask> nudgeIdleAgent(String memberName, boolean isFromPoll) {
    List<TeamTask> allTasks = host.getTaskManager().list();
    List<TeamTask> incomplete =
        allTasks.stream()
            .filter(task -> !"completed".equals(task.getStatus()))
            .filter(task -> !"cancelled".equals(task.getStatus()))
            .toList();
    if (isFromPoll && host.getContext().getRole() == TeamRole.LEADER && incomplete.isEmpty()) {
      return incomplete;
    }
    if (host.getContext().getRole() == TeamRole.LEADER) {
      if (incomplete.isEmpty()) {
        String lifecycle = host.getSpec() != null ? host.getSpec().getLifecycle() : "temporary";
        if ("persistent".equalsIgnoreCase(lifecycle)) {
          host.deliverInput("All tasks are complete. Stay available for the next user request.");
        } else {
          host.deliverInput("All tasks are complete. Wrap up the team run.");
        }
        return incomplete;
      }
      List<String> lines = new ArrayList<>();
      lines.add("Current task board:");
      appendTaskLines(lines, incomplete);
      host.deliverInput(String.join("\n", lines));
      return incomplete;
    }

    List<TeamTask> claimable =
        incomplete.stream()
            .filter(task -> "pending".equals(task.getStatus()))
            .filter(task -> task.getAssignee() == null || task.getAssignee().isBlank())
            .toList();
    if (claimable.isEmpty() && incomplete.isEmpty()) {
      return incomplete;
    }
    List<String> lines = new ArrayList<>();
    lines.add("Current teammate task list:");
    appendTaskLines(lines, incomplete);
    host.deliverInput(String.join("\n", lines));
    return incomplete;
  }

  /** Auto-generated for codecheck compliance. */
  public List<TeamTask> checkStaleClaimedTasks() {
    long now = System.currentTimeMillis();
    String ownName = host.resolveLocalMemberName();
    boolean isLeader = host.getContext().getRole() == TeamRole.LEADER;
    List<TeamTask> relevant =
        host.getTaskManager().list().stream()
            .filter(task -> "claimed".equals(task.getStatus()))
            .filter(task -> task.getAssignee() != null && !task.getAssignee().isBlank())
            .filter(task -> isLeader || task.getAssignee().equals(ownName))
            .toList();
    Set<String> currentIds =
        relevant.stream().map(TeamTask::getTaskId).collect(java.util.stream.Collectors.toSet());
    lastStaleNudgeMillis.keySet().removeIf(taskId -> !currentIds.contains(taskId));
    List<TeamTask> stale = new ArrayList<>();
    for (TeamTask task : relevant) {
      if (now - task.getUpdatedAt() < STALE_CLAIM_MILLIS) {
        continue;
      }
      if (now - lastStaleNudgeMillis.getOrDefault(task.getTaskId(), 0L) < STALE_CLAIM_MILLIS) {
        continue;
      }
      lastStaleNudgeMillis.put(task.getTaskId(), now);
      stale.add(task);
      String content =
          "Stale claimed task ["
              + task.getTaskId()
              + "] "
              + task.getTitle()
              + ": "
              + task.getContent();
      if (task.getAssignee().equals(ownName)) {
        host.deliverInput(content);
      } else if (isLeader) {
        host.getMessageManager().sendMessage(content, task.getAssignee()).join();
      }
    }
    return stale;
  }

  /** Auto-generated for codecheck compliance. */
  public List<TeamTask> checkStalePendingTasks() {
    if (host.getContext().getRole() != TeamRole.LEADER) {
      return List.of();
    }
    long now = System.currentTimeMillis();
    List<TeamTask> pending =
        host.getTaskManager().list().stream()
            .filter(task -> "pending".equals(task.getStatus()))
            .toList();
    Set<String> staleIds =
        pending.stream()
            .filter(task -> now - task.getUpdatedAt() >= STALE_PENDING_MILLIS)
            .map(TeamTask::getTaskId)
            .collect(java.util.stream.Collectors.toSet());
    lastPendingNudgeMillis.keySet().removeIf(taskId -> !staleIds.contains(taskId));
    List<TeamTask> fresh =
        pending.stream()
            .filter(task -> staleIds.contains(task.getTaskId()))
            .filter(
                task ->
                    now - lastPendingNudgeMillis.getOrDefault(task.getTaskId(), 0L)
                        >= STALE_PENDING_MILLIS)
            .toList();
    if (fresh.isEmpty()) {
      return fresh;
    }
    for (TeamTask task : fresh) {
      lastPendingNudgeMillis.put(task.getTaskId(), now);
    }
    List<String> lines = new ArrayList<>();
    lines.add(
        "Some pending tasks have not been claimed. Pick the right teammate and use send_message or"
            + " claim_task.");
    for (TeamTask task : fresh) {
      lines.add("- [" + task.getTaskId() + "] " + task.getTitle() + ": " + task.getContent());
    }
    host.deliverInput(String.join("\n", lines));
    return fresh;
  }

  /** Auto-generated for codecheck compliance. */
  public Map<String, Long> getLastPendingNudgeMillis() {
    return Map.copyOf(lastPendingNudgeMillis);
  }

  /** Auto-generated for codecheck compliance. */
  public Map<String, Long> getLastStaleNudgeMillis() {
    return Map.copyOf(lastStaleNudgeMillis);
  }

  /** Auto-generated for codecheck compliance. */
  public boolean ackUserBoundMessage(EventMessage event) {
    Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Map.of();
    String toMemberName = stringValue(payload.get("to_member_name"));
    String messageId = stringValue(payload.get("message_id"));
    if (!TeamConstants.USER_PSEUDO_MEMBER_NAME.equals(toMemberName)
        || messageId == null
        || messageId.isBlank()) {
      return false;
    }
    return host.getMessageManager()
        .markMessageRead(messageId, TeamConstants.USER_PSEUDO_MEMBER_NAME);
  }

  /** Auto-generated for codecheck compliance. */
  public InteractiveInput handleToolApprovalResult(EventMessage event) {
    Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Map.of();
    String memberName = stringValue(payload.get("member_name"));
    if (memberName == null || !memberName.equals(host.currentMemberName())) {
      return null;
    }
    String toolCallId = stringValue(payload.get("tool_call_id"));
    if (toolCallId == null || toolCallId.isBlank()) {
      return null;
    }
    Map<String, Object> decision = new LinkedHashMap<>();
    decision.put("approved", booleanValue(payload.get("approved")));
    decision.put("feedback", stringValue(payload.get("feedback")));
    decision.put("auto_confirm", booleanValue(payload.get("auto_confirm")));
    InteractiveInput input = new InteractiveInput();
    input.update(toolCallId, decision);
    host.resumeInterrupt(input);
    return input;
  }

  /** Auto-generated for codecheck compliance. */
  public boolean handleTeammateMemberLifecycleEvent(EventMessage event) {
    if (host.getContext().getRole() == TeamRole.LEADER) {
      return false;
    }
    Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Map.of();
    String memberName = stringValue(payload.get("member_name"));
    if (memberName == null || !memberName.equals(host.currentMemberName())) {
      return false;
    }
    if (EVENT_MEMBER_CANCELED.equals(event.getEventType())) {
      host.cancelAgent();
      return true;
    }
    if (EVENT_MEMBER_SHUTDOWN.equals(event.getEventType())) {
      processUnreadMessages(memberName, true);
      return true;
    }
    return false;
  }

  /** Auto-generated for codecheck compliance. */
  public List<TeamMessage> processUnreadMessages(String memberName) {
    return processUnreadMessages(memberName, true);
  }

  /** Auto-generated for codecheck compliance. */
  public List<TeamMessage> processUnreadMessages(String memberName, boolean isUseSteer) {
    if (memberName == null || memberName.isBlank() || host.hasPendingInterrupt()) {
      return List.of();
    }
    Set<String> seenIds = ConcurrentHashMap.newKeySet();
    List<TeamMessage> delivered = new ArrayList<>();
    while (true) {
      List<TeamMessage> unread =
          readAllUnread(memberName).stream()
              .filter(message -> seenIds.add(message.getMessageId()))
              .toList();
      if (unread.isEmpty()) {
        break;
      }
      for (TeamMessage message : unread) {
        if (host.hasPendingInterrupt()) {
          return delivered;
        }
        host.deliverInput(formatMessage(message), isUseSteer);
        host.getMessageManager().markMessageRead(message.getMessageId(), memberName);
        delivered.add(message);
      }
    }
    return delivered;
  }

  /** Auto-generated for codecheck compliance. */
  public String formatMessage(TeamMessage message) {
    if (message == null) {
      return "";
    }
    String messageType = message.isBroadcast() ? "broadcast" : "direct";
    return "Received "
        + messageType
        + " message"
        + " ["
        + message.getMessageId()
        + "]"
        + " from "
        + message.getFromMemberName()
        + ": "
        + message.getContent();
  }

  /** Auto-generated for codecheck compliance. */
  public List<TeamMessage> readAllUnread(String memberName) {
    List<TeamMessage> unread = new ArrayList<>();
    unread.addAll(host.getMessageManager().getMessages(memberName, true));
    unread.addAll(host.getMessageManager().getBroadcastMessages(true));
    unread.sort(Comparator.comparingLong(TeamMessage::getTimestamp).reversed());
    return unread;
  }

  private static String stringValue(Object value) {
    return value != null ? String.valueOf(value) : null;
  }

  private static boolean booleanValue(Object value) {
    if (value instanceof Boolean boolValue) {
      return boolValue;
    }
    return value != null && Boolean.parseBoolean(String.valueOf(value));
  }

  private static boolean isTaskEvent(String type) {
    return EVENT_TASK_CREATED.equals(type)
        || EVENT_TASK_UPDATED.equals(type)
        || EVENT_TASK_CLAIMED.equals(type)
        || EVENT_TASK_COMPLETED.equals(type)
        || EVENT_TASK_CANCELLED.equals(type)
        || EVENT_TASK_UNBLOCKED.equals(type);
  }

  private static boolean isMemberEvent(String type) {
    return EVENT_MEMBER_SPAWNED.equals(type)
        || EVENT_MEMBER_RESTARTED.equals(type)
        || EVENT_MEMBER_STATUS_CHANGED.equals(type)
        || EVENT_MEMBER_EXECUTION_CHANGED.equals(type)
        || EVENT_MEMBER_SHUTDOWN.equals(type)
        || EVENT_MEMBER_CANCELED.equals(type);
  }

  private static void appendTaskLines(List<String> lines, List<TeamTask> tasks) {
    for (TeamTask task : tasks) {
      String assignee =
          task.getAssignee() != null && !task.getAssignee().isBlank()
              ? " -> " + task.getAssignee()
              : " (unassigned)";
      lines.add(
          "- ["
              + task.getTaskId()
              + "] ["
              + task.getStatus()
              + "] "
              + task.getTitle()
              + ": "
              + task.getContent()
              + assignee);
    }
  }
}
