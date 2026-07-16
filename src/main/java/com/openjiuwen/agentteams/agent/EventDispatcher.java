/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.interaction.MentionRoute;
import com.openjiuwen.agentteams.interaction.Router;
import com.openjiuwen.agentteams.interaction.UserInbox;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.tools.TeamMessage;
import com.openjiuwen.agentteams.tools.TeamResultCollector;
import com.openjiuwen.agentteams.tools.TeamTask;
import com.openjiuwen.agentteams.tools.database.MemberRecord;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.session.interaction.InteractiveInput;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * First Java dispatcher slice for team coordination events.
 * <p>
 * This mirrors the locally verifiable Python {@code agent/dispatcher.py} paths: inner user
 * input, mailbox polling, and simple transport control events. Stale task nudges, tool approval
 * resumes, and full task-board orchestration remain later slices.
 * 
 * @since 0.1.7
 */
public class EventDispatcher {
    /**
     * EVENT_STANDBY.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_STANDBY = "team_standby";

    /**
     * EVENT_CLEANED.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_CLEANED = "team_cleaned";

    /**
     * EVENT_MESSAGE.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_MESSAGE = "message";

    /**
     * EVENT_BROADCAST.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_BROADCAST = "broadcast";

    /**
     * EVENT_TASK_CREATED.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_TASK_CREATED = "task_created";

    /**
     * EVENT_TASK_UPDATED.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_TASK_UPDATED = "task_updated";

    /**
     * EVENT_TASK_CLAIMED.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_TASK_CLAIMED = "task_claimed";

    /**
     * EVENT_TASK_COMPLETED.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_TASK_COMPLETED = "task_completed";

    /**
     * EVENT_TASK_CANCELLED.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_TASK_CANCELLED = "task_cancelled";

    /**
     * EVENT_TASK_UNBLOCKED.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_TASK_UNBLOCKED = "task_unblocked";

    /**
     * EVENT_MEMBER_SPAWNED.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_MEMBER_SPAWNED = "member_spawned";

    /**
     * EVENT_MEMBER_RESTARTED.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_MEMBER_RESTARTED = "member_restarted";

    /**
     * EVENT_MEMBER_STATUS_CHANGED.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_MEMBER_STATUS_CHANGED = "member_status_changed";

    /**
     * EVENT_MEMBER_EXECUTION_CHANGED.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_MEMBER_EXECUTION_CHANGED = "member_execution_changed";

    /**
     * EVENT_TOOL_APPROVAL_RESULT.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_TOOL_APPROVAL_RESULT = "tool_approval_result";

    /**
     * EVENT_MEMBER_CANCELED.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_MEMBER_CANCELED = "member_canceled";

    /**
     * EVENT_MEMBER_SHUTDOWN.
     * 
     * @since 0.1.7
     */
    public static final String EVENT_MEMBER_SHUTDOWN = "member_shutdown";

    /**
     * STALE_CLAIM_MILLIS.
     * 
     * @since 0.1.7
     */
    public static final long STALE_CLAIM_MILLIS = 60 * 1000L;

    /**
     * STALE_PENDING_MILLIS.
     * 
     * @since 0.1.7
     */
    public static final long STALE_PENDING_MILLIS = 10 * 60 * 1000L;

    private final TeamAgent host;
    @SuppressWarnings("unused")
    private volatile boolean streamingActive;

    /**
     * Per-stage delivery guard — static so each stage delivers exactly once across all members.
     * 
     * @since 0.1.7
     */
    private static final java.util.Set<String> deliveredStages = ConcurrentHashMap.newKeySet();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Long> lastStaleNudgeMillis = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Long> lastPendingNudgeMillis = new ConcurrentHashMap<>();

    /**
     * EventDispatcher.
     * 
     * @param host host
     * @since 0.1.7
     */
    public EventDispatcher(TeamAgent host) {
        this.host = Objects.requireNonNull(host, "host is required");
    }

    /**
     * dispatch.
     * 
     * @param event event
     * @since 0.1.7
     */
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

    /**
     * handleInnerEvent.
     * 
     * @param event event
     * @since 0.1.7
     */
    public void handleInnerEvent(InnerEventMessage event) {
        if (event == null || event.getEventType() == null) {
            return;
        }
        if (event.getEventType() == InnerEventType.USER_INPUT) {
            Object content = event.getPayload() != null ? event.getPayload().get("content") : null;
            String text = content != null ? String.valueOf(content) : "";
            // @mention resolution: route @member_name message directly to target
            if (resolveMention(text)) {
                return;
            }
            host.deliverInput(text);
            return;
        }
        if (event.getEventType() == InnerEventType.POLL_MAILBOX) {
            String memberName = host.resolveLocalMemberName();
            Loggers.AGENT.debug("EventDispatcher: POLL_MAILBOX for member={} role={}", memberName,
                    host.getContext().getRole());
            processUnreadMessages(memberName);
            return;
        }
        if (event.getEventType() == InnerEventType.POLL_TASK) {
            checkStaleClaimedTasks();
            checkStalePendingTasks();
        }
    }

    /**
     * Parse {@code @member_name message} and send as a direct message
     * to the target member.
     * 
     * @param content content
     * @return true if the content was routed as a mention
     * @since 0.1.7
     */
    private boolean resolveMention(String content) {
        Optional<MentionRoute> parsed = Router.parseMention(content);
        if (parsed.isEmpty()) {
            return false;
        }
        MentionRoute route = parsed.get();
        String target = route.target();
        String body = route.body();

        // Check if target exists in the team
        var member = host.getTeamBackend().getMember(target);
        if (member == null) {
            Loggers.AGENT.warn("@mention target '{}' not found in database, falling through", target);
            return false;
        }

        sendUserDirectMessage(target, body);
        return true;
    }

    /**
     * sendUserDirectMessage.
     * 
     * @param toMemberName toMemberName
     * @param content content
     * @since 0.1.7
     */
    private void sendUserDirectMessage(String toMemberName, String content) {
        var mm = host.getMessageManager();
        if (mm == null) {
            Loggers.AGENT.warn("messageManager unavailable, cannot send user direct message");
            return;
        }
        UserInbox inbox = new UserInbox(mm);
        inbox.direct(toMemberName, content)
                .thenAccept(msgId -> Loggers.AGENT.info("user direct message sent to {}: {}", toMemberName, msgId));
    }

    /**
     * handleTransportEvent.
     * 
     * @param event event
     * @since 0.1.7
     */
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
            Loggers.AGENT.debug("EventDispatcher: {} for member={} role={}", type, host.resolveLocalMemberName(),
                    host.getContext().getRole());
            if (EVENT_MESSAGE.equals(type) && host.getContext().getRole() == TeamRole.LEADER) {
                ackUserBoundMessage(event);
            }
            host.resumePolls();
            processUnreadMessages(host.resolveLocalMemberName());
            return;
        }
        // Framework-delivered upstream results — only the intended target processes it
        if ("member_results_delivery".equals(type)) {
            Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Map.of();
            String target = payload.get("target_assignee") instanceof String s ? s : "";
            if (!target.isBlank() && !target.equals(localMember)) {
                return; // not for me
            }
            String content = payload.get("content") instanceof String s ? s : "";
            if (!content.isBlank()) {
                Loggers.AGENT.info("EventDispatcher: received member_results_delive"
                        + "ry for member={}, delivering directly ({} chars)", localMember, content.length());
                host.resumePolls();
                host.deliverInput(content);
            }
            return;
        }
        if (EVENT_TASK_CLAIMED.equals(type)) {
            if (handleTaskClaimed(event)) {
                return;
            }
        }
        if (isTaskEvent(type)) {
            Loggers.AGENT.info("EventDispatcher: received task event type={} for member={} role={} hasInFlightRound={}",
                    type, host.resolveLocalMemberName(), host.getContext().getRole(), host.hasInFlightRound());
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

    /**
     * handleTaskClaimed.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * handleMemberStatusChanged.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * handleLeaderMemberLifecycleEvent.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
    public String handleLeaderMemberLifecycleEvent(EventMessage event) {
        if (host.getContext().getRole() != TeamRole.LEADER || event == null) {
            return null;
        }
        Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Map.of();
        String memberName = stringValue(payload.get("member_name"));
        return switch (event.getEventType()) {
            case EVENT_MEMBER_SPAWNED -> "Member online: " + memberName;
            case EVENT_MEMBER_RESTARTED ->
                "Member restarted: " + memberName + " restart_count=" + payload.getOrDefault("restart_count", 1);
            case EVENT_MEMBER_STATUS_CHANGED -> {
                String oldStatus = stringValue(payload.get("old_status"));
                String newStatus = stringValue(payload.get("new_status"));
                nudgeIdleMemberWithStaleClaims(memberName, oldStatus, newStatus);
                yield "Member status changed: " + memberName + " " + oldStatus + " -> " + newStatus;
            }
            case EVENT_MEMBER_EXECUTION_CHANGED -> "Member execution changed: " + memberName + " "
                    + payload.get("old_status") + " -> " + payload.get("new_status");
            case EVENT_MEMBER_SHUTDOWN -> "Member shutdown: " + memberName;
            case EVENT_MEMBER_CANCELED -> "Member canceled: " + memberName;
            default -> null;
        };
    }

    /**
     * nudgeIdleMemberWithStaleClaims.
     * 
     * @param memberName memberName
     * @param oldStatus oldStatus
     * @param newStatus newStatus
     * @return the result
     * @since 0.1.7
     */
    public List<TeamTask> nudgeIdleMemberWithStaleClaims(String memberName, String oldStatus, String newStatus) {
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
        List<TeamTask> stale = host.getTaskManager().getTasksByAssignee(memberName, "claimed").stream()
                .filter(task -> now - task.getUpdatedAt() >= STALE_CLAIM_MILLIS).toList();
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

    /**
     * handleTaskBoardEvent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<TeamTask> handleTaskBoardEvent() {
        host.resumePolls();
        return nudgeIdleAgent(host.resolveLocalMemberName(), false);
    }

    /**
     * nudgeIdleAgent.
     * 
     * @param memberName memberName
     * @param isFromPoll isFromPoll
     * @return the result
     * @since 0.1.7
     */
    public List<TeamTask> nudgeIdleAgent(String memberName, boolean isFromPoll) {
        List<TeamTask> allTasks = host.getTaskManager().list();
        List<TeamTask> incomplete = allTasks.stream().filter(task -> !"completed".equals(task.getStatus()))
                .filter(task -> !"cancelled".equals(task.getStatus())).toList();
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
                    // Stop the coordinator loop for temporary teams. Matches Python's
                    // finalize_round which calls self.stop() for temporary lifecycles.
                    if (host.getCoordinatorLoop() != null && host.getCoordinatorLoop().isRunning()) {
                        host.getCoordinatorLoop().stop();
                    }
                }
                return incomplete;
            }
            List<String> lines = new ArrayList<>();
            lines.add("Current task board:");
            appendTaskLines(lines, incomplete);
            host.deliverInput(String.join("\n", lines));
            return incomplete;
        }

        List<TeamTask> claimable = incomplete.stream().filter(task -> "pending".equals(task.getStatus()))
                .filter(task -> task.getAssignee() == null || task.getAssignee().isBlank()).toList();
        if (claimable.isEmpty() && incomplete.isEmpty()) {
            return incomplete;
        }
        List<String> lines = new ArrayList<>();
        lines.add("Current teammate task list:");
        appendTaskLines(lines, incomplete);
        host.deliverInput(String.join("\n", lines));
        return incomplete;
    }

    /**
     * checkStaleClaimedTasks.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<TeamTask> checkStaleClaimedTasks() {
        long now = System.currentTimeMillis();
        String ownName = host.resolveLocalMemberName();
        boolean isLeader = host.getContext().getRole() == TeamRole.LEADER;
        List<TeamTask> allTasks = host.getTaskManager().list();
        List<TeamTask> relevant = allTasks.stream().filter(task -> "claimed".equals(task.getStatus()))
                .filter(task -> task.getAssignee() != null && !task.getAssignee().isBlank())
                .filter(task -> isLeader || task.getAssignee().equals(ownName)).toList();
        Loggers.AGENT.debug("checkStaleClaimedTasks: member={} role={} dbHash={} totalTasks={} claimedRelevant={}",
                ownName, host.getContext().getRole(),
                Integer.toHexString(System.identityHashCode(host.getTeamBackend().getDb())), allTasks.size(),
                relevant.size());
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
                "Stale claimed task [" + task.getTaskId() + "] " + task.getTitle() + ": " + task.getContent();
            if (task.getAssignee().equals(ownName)) {
                if (isLeader) {
                    host.deliverInput(content);
                } else {
                    // Non-leader member with a stale claimed task: auto-complete it.
                    // This is the safety net for members whose agent round is stuck
                    // (never called tryAutoCompleteMemberTasks).
                    Loggers.AGENT.info(
                            "checkStaleClaimedTasks: auto-completing stale"
                                    + " task [{}] {} for member={} (claimed for {}s)",
                            task.getTaskId(), task.getTitle(), ownName, (now - task.getUpdatedAt()) / 1000);
                    var result = host.getTaskManager().completeResult(task.getTaskId()).join();
                    Loggers.AGENT.info("checkStaleClaimedTasks: auto-complete result ok={} reason={}", result.isOk(),
                            result.isOk() ? "" : result.getReason());
                }
            } else if (isLeader) {
                host.getMessageManager().sendMessage(content, task.getAssignee()).join();
            } else {
                // no-op
            }
        }
        return stale;
    }

    /**
     * checkStalePendingTasks.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<TeamTask> checkStalePendingTasks() {
        if (host.getContext().getRole() != TeamRole.LEADER) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        List<TeamTask> pending =
            host.getTaskManager().list().stream().filter(task -> "pending".equals(task.getStatus())).toList();
        Set<String> staleIds = pending.stream().filter(task -> now - task.getUpdatedAt() >= STALE_PENDING_MILLIS)
                .map(TeamTask::getTaskId).collect(java.util.stream.Collectors.toSet());
        lastPendingNudgeMillis.keySet().removeIf(taskId -> !staleIds.contains(taskId));
        List<TeamTask> fresh = pending.stream().filter(task -> staleIds.contains(task.getTaskId()))
                .filter(task -> now - lastPendingNudgeMillis.getOrDefault(task.getTaskId(), 0L) >= STALE_PENDING_MILLIS)
                .toList();
        if (fresh.isEmpty()) {
            return fresh;
        }
        for (TeamTask task : fresh) {
            lastPendingNudgeMillis.put(task.getTaskId(), now);
        }
        List<String> lines = new ArrayList<>();
        lines.add("Some pending tasks have not been claimed. Pick the right teammate and use send_message or"
                + " claim_task.");
        for (TeamTask task : fresh) {
            lines.add("- [" + task.getTaskId() + "] " + task.getTitle() + ": " + task.getContent());
        }
        host.deliverInput(String.join("\n", lines));
        return fresh;
    }

    /**
     * getLastPendingNudgeMillis.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Long> getLastPendingNudgeMillis() {
        return Map.copyOf(lastPendingNudgeMillis);
    }

    /**
     * getLastStaleNudgeMillis.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Long> getLastStaleNudgeMillis() {
        return Map.copyOf(lastStaleNudgeMillis);
    }

    /**
     * ackUserBoundMessage.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
    public boolean ackUserBoundMessage(EventMessage event) {
        Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Map.of();
        String toMemberName = stringValue(payload.get("to_member_name"));
        String messageId = stringValue(payload.get("message_id"));
        if (!TeamConstants.USER_PSEUDO_MEMBER_NAME.equals(toMemberName) || messageId == null || messageId.isBlank()) {
            return false;
        }
        return host.getMessageManager().markMessageRead(messageId, TeamConstants.USER_PSEUDO_MEMBER_NAME);
    }

    /**
     * handleToolApprovalResult.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * handleTeammateMemberLifecycleEvent.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
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
            // After processing the shutdown message, update DB status to SHUTDOWN
            // so the leader's clean_team can succeed. Mirrors the implicit status
            // update that happens on process exit in Python's multi-process mode.
            host.shutdownSelf();
            return true;
        }
        return false;
    }

    /**
     * processUnreadMessages.
     * 
     * @param memberName memberName
     * @return the result
     * @since 0.1.7
     */
    public List<TeamMessage> processUnreadMessages(String memberName) {
        return processUnreadMessages(memberName, true);
    }

    /**
     * processUnreadMessages.
     * 
     * @param memberName memberName
     * @param isUseSteer isUseSteer
     * @return the result
     * @since 0.1.7
     */
    public List<TeamMessage> processUnreadMessages(String memberName, boolean isUseSteer) {
        if (memberName == null || memberName.isBlank() || host.hasPendingInterrupt()) {
            Loggers.AGENT.debug("processUnreadMessages: skipped member={} hasPendingInterrupt={}", memberName,
                    host.hasPendingInterrupt());
            return List.of();
        }
        Set<String> seenIds = ConcurrentHashMap.newKeySet();
        List<TeamMessage> delivered = new ArrayList<>();
        while (true) {
            List<TeamMessage> unread =
                readAllUnread(memberName).stream().filter(message -> seenIds.add(message.getMessageId())).toList();
            Loggers.AGENT.debug("processUnreadMessages: member={} found {} unread message(s)", memberName,
                    unread.size());
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

    /**
     * Called after a member's agent round completes.
     * <p>
     * The round IS the unit of work. If the member sent a result to the leader
     * during this round, their task is done. We check two cases:
     * 1. Claimed tasks (assignee = this member) → round end = work done → auto-complete
     * 2. Pending tasks matching this member's role → auto-claim (stale check completes later)
     * 
     * @since 0.1.7
     */
    public void tryAutoCompleteMemberTasks() {
        if (host.getContext() == null) {
            return;
        }
        boolean isLeader = host.getContext().getRole() == TeamRole.LEADER;
        if (isLeader && !host.hasInFlightRound()) {
            // Leader may claim its own summary task and forget to complete it.
            // Auto-complete leader tasks too so they don't block delivery.
            autoCompleteLeaderTasks();
            return;
        }
        if (isLeader) {
            return;
        }
        if (host.hasInFlightRound()) {
            return;
        }
        String memberName = host.resolveLocalMemberName();
        var tasks = host.getTaskManager().list();
        Loggers.AGENT.info("tryAutoCompleteMemberTasks: round-end check for member={} totalTasks={}", memberName,
                tasks.size());

        // Signal: did this member deliver output to the leader in this round?
        // If they sent a message to team_leader, the work is done.
        boolean sentResultToLeader = hasSentMessageToLeader(memberName);
        Loggers.AGENT.info("tryAutoCompleteMemberTasks: member={} sentResultToLeader={}", memberName,
                sentResultToLeader);

        if (!sentResultToLeader) {
            // No result sent yet — the member might still be working.
            // Auto-claim pending tasks but don't complete (let stale check handle it).
            var pendingForMe = tasks.stream().filter(t -> "pending".equals(t.getStatus()))
                    .filter(t -> t.getAssignee() == null || t.getAssignee().isBlank())
                    .filter(t -> memberName.equals(t.getAssignee())).toList();
            for (var task : pendingForMe) {
                Loggers.AGENT.info(
                        "tryAutoCompleteMemberTasks: auto-claiming pendin"
                                + "g task [{}] {} for member={} (no result sent yet)",
                        task.getTaskId(), task.getTitle(), memberName);
                host.getTaskManager().claimResult(task.getTaskId()).join();
            }
            return;
        }

        // result WAS sent to leader — work is definitely done.
        // 1. Collect the result content for framework-driven delivery
        collectResultFromMessages(memberName);

        // 2. Auto-complete ALL tasks belonging to this member
        var myTasks = tasks.stream().filter(t -> {
            String status = t.getStatus();
            if ("completed".equals(status) || "cancelled".equals(status)) {
                return false;
            }
            return memberName.equals(t.getAssignee());
        }).toList();

        for (var task : myTasks) {
            String status = task.getStatus();
            Loggers.AGENT.info(
                    "tryAutoCompleteMemberTasks: member={} sent result, auto-completing task [{}] {} (status={})",
                    memberName, task.getTaskId(), task.getTitle(), status);
            if ("pending".equals(status)) {
                host.getTaskManager().claimResult(task.getTaskId()).join();
            }
            host.getTaskManager().completeResult(task.getTaskId()).join();
        }

        // 3. Always try to deliver — results may have been collected in this round
        // even if tasks were already completed in a previous round.
        tryDeliverToNextStage();
    }

    /**
     * Check if this member sent output to the leader during this round.
     * Uses the same query path as the message system.
     * 
     * @param memberName memberName
     * @return the result
     * @since 0.1.7
     */
    private boolean hasSentMessageToLeader(String memberName) {
        try {
            var mm = host.getMessageManager();
            if (mm == null) {
                return false;
            }
            String leaderName = resolveLeaderName();
            var allMessages = mm.getMessages(leaderName, false);
            long matchCount = allMessages.stream().filter(m -> memberName.equals(m.getFromMemberName())).count();
            Loggers.AGENT.info("hasSentMessageToLeader: member={} leaderName={} leaderInboxTotal={} fromMemberMatch={}",
                    memberName, leaderName, allMessages.size(), matchCount);
            return matchCount > 0;
        } catch (Exception e) {
            Loggers.AGENT.warn("hasSentMessageToLeader: error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * resolveLeaderName.
     * 
     * @return the result
     * @since 0.1.7
     */
    private String resolveLeaderName() {
        try {
            var db = host.getTeamBackend().getDb();
            if (db != null) {
                return db.member.getTeamMembers(host.getTeamBackend().getTeamName()).stream()
                        .map(MemberRecord::getMemberName).filter(name -> name != null && name.contains("leader"))
                        .findFirst().orElse("team_leader");
            }
        } catch (Exception ignored) {

            // Ignore.
        }
        return "team_leader";
    }

    /**
     * Collect this member's output (sent via send_message to the leader)
     * into the {@link TeamResultCollector} for framework-driven delivery.
     * 
     * @since 0.1.7
     */
    private void autoCompleteLeaderTasks() {
        String memberName = host.resolveLocalMemberName();
        var tasks = host.getTaskManager().list();
        var myTasks =
            tasks.stream().filter(t -> !"completed".equals(t.getStatus()) && !"cancelled".equals(t.getStatus()))
                    .filter(t -> memberName.equals(t.getAssignee())).toList();
        for (var task : myTasks) {
            Loggers.AGENT.info("autoCompleteLeaderTasks: auto-completing leader task [{}] {} status={}",
                    task.getTaskId(), task.getTitle(), task.getStatus());
            host.getTaskManager().completeResult(task.getTaskId()).join();
        }
        // Do NOT call tryDeliverResultsToLeader() here — the leader's round ends
        // before any member result are collected, causing an empty delivery that
        // blocks subsequent real deliveries. Member tryAutoCompleteMemberTasks
        // handles delivery when it actually has result.
    }

    /**
     * collectResultFromMessages.
     * 
     * @param memberName memberName
     * @since 0.1.7
     */
    private void collectResultFromMessages(String memberName) {
        try {
            var mm = host.getMessageManager();
            if (mm == null) {
                return;
            }
            // Use the same API that works for hasSentMessageToLeader
            var allMessages = mm.getMessages("team_leader", false);
            var fromMe = allMessages.stream().filter(m -> memberName.equals(m.getFromMemberName())).toList();
            if (fromMe.isEmpty()) {
                Loggers.AGENT.info("collectResultFromMessages: no messages from {} in leader inbox", memberName);
                return;
            }
            String teamName = host.getTeamBackend().getTeamName();
            for (var msg : fromMe) {
                String content = msg.getContent();
                if (content != null && !content.isBlank()) {
                    TeamResultCollector.add(teamName, memberName, content);
                    Loggers.AGENT.info("collectResultFromMessages: captured result from {} ({} chars)", memberName,
                            content.length());
                }
            }
        } catch (Exception e) {
            Loggers.AGENT.warn("collectResultFromMessages: error for {}: {}", memberName, e.getMessage());
        }
    }

    /**
     * Multi-stage delivery: find tasks whose dependencies just became satisfied,
     * collect the outputs of those dependency tasks, and deliver them to the
     * assignee of the newly-unblocked task.
     * <p>
     * This generalises the single "all result to leader" pattern to support
     * multi-round team skills where researchers, portfolio managers, or other
     * roles consume upstream outputs before the leader does final assembly.
     * 
     * @since 0.1.7
     */
    private void tryDeliverToNextStage() {
        String teamName = host.getTeamBackend().getTeamName();
        var allTasks = host.getTaskManager().list();
        var allResults = TeamResultCollector.getAll(teamName);

        // Find tasks whose dependencies are ALL completed but the task itself
        // hasn't been delivered to yet. These are the next-stage consumers.
        for (var task : allTasks) {
            if ("completed".equals(task.getStatus()) || "cancelled".equals(task.getStatus())) {
                continue;
            }
            if (task.getDependencies() == null || task.getDependencies().isEmpty()) {
                continue;
            }
            String assignee = task.getAssignee();
            if (assignee == null || assignee.isBlank()) {
                continue;
            }

            // Check if all dependencies are complete
            boolean depsDone = task.getDependencies().stream().allMatch(depId -> allTasks.stream()
                    .anyMatch(t -> depId.equals(t.getTaskId()) && "completed".equals(t.getStatus())));
            if (!depsDone) {
                continue;
            }

            // Build delivery: collect outputs from the dependency tasks
            StringBuilder sb = new StringBuilder();
            boolean isFinalStage = assignee != null && assignee.contains("leader");
            if (isFinalStage) {
                sb.append("[SYSTEM] All upstream work is complete. YOU are the leader — ");
                sb.append("only you can complete this final task.\n");
                sb.append("Write the final output NOW using file_io, then call ");
                sb.append("claim_task(status=\"completed\") on your task.\n");
                sb.append("Do NOT cancel this task. Do NOT delegate to another member.\n\n");
            } else {
                sb.append("[SYSTEM] The following upstream work has been completed. ");
                sb.append("Use these outputs to produce your response.\n\n");
            }
            sb.append("---\n\n");

            int collected = 0;
            for (String depId : task.getDependencies()) {
                for (var entry : allResults.entrySet()) {
                    if (collected > 0) {
                        break; // just collect per-dep once
                    }
                }
                // Collect result whose memberName matches the dependency assignee
                for (var depTask : allTasks) {
                    if (depId.equals(depTask.getTaskId()) && depTask.getAssignee() != null) {
                        String depAssignee = depTask.getAssignee();
                        String result = allResults.get(depAssignee);
                        if (result != null) {
                            sb.append("## ").append(depAssignee).append("\n\n");
                            sb.append(result);
                            sb.append("\n\n---\n\n");
                            collected++;
                        }
                        break;
                    }
                }
            }

            if (collected == 0) {
                // No result ready for this stage yet — collect more first
                Loggers.AGENT.info("tryDeliverToNextStage: task [{}] deps done but 0 result collected yet, waiting",
                        task.getTaskId());
                continue;
            }

            // Prevent duplicate delivery per stage
            String stageKey = teamName + "/" + task.getTaskId();
            if (!deliveredStages.add(stageKey)) {
                continue;
            }

            String deliveryMessage = sb.toString();
            Loggers.AGENT.info(
                    "tryDeliverToNextStage: delivering {} dependency outputs to assignee={} for task [{}] ({} chars)",
                    collected, assignee, task.getTaskId(), deliveryMessage.length());

            try {
                var messager = host.getTeamBackend().getMessager();
                if (messager != null) {
                    messager.publish("team:message", com.openjiuwen.agentteams.schema.events.EventMessage.builder()
                            .eventType("member_results_delivery").payload(java.util.Map.of("content", deliveryMessage,
                                    "from_member", host.resolveLocalMemberName(), "target_assignee", assignee))
                            .build()).join();
                    Loggers.AGENT.info("tryDeliverToNextStage: results published for task [{}] assignee={}",
                            task.getTaskId(), assignee);
                }
            } catch (Exception e) {
                Loggers.AGENT.error("tryDeliverToNextStage: failed to deliver: {}", e.getMessage());
            }
        }
    }

    /**
     * formatMessage.
     * 
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    public String formatMessage(TeamMessage message) {
        if (message == null) {
            return "";
        }
        String messageType = message.isBroadcast() ? "broadcast" : "direct";
        return "Received " + messageType + " message" + " [" + message.getMessageId() + "]" + " from "
                + message.getFromMemberName() + ": " + message.getContent();
    }

    /**
     * readAllUnread.
     * 
     * @param memberName memberName
     * @return the result
     * @since 0.1.7
     */
    public List<TeamMessage> readAllUnread(String memberName) {
        List<TeamMessage> unread = new ArrayList<>();
        unread.addAll(host.getMessageManager().getMessages(memberName, true));
        unread.addAll(host.getMessageManager().getBroadcastMessages(true));
        unread.sort(Comparator.comparingLong(TeamMessage::getTimestamp).reversed());
        return unread;
    }

    /**
     * stringValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * booleanValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * isTaskEvent.
     * 
     * @param type type
     * @return the result
     * @since 0.1.7
     */
    private static boolean isTaskEvent(String type) {
        return EVENT_TASK_CREATED.equals(type) || EVENT_TASK_UPDATED.equals(type) || EVENT_TASK_CLAIMED.equals(type)
                || EVENT_TASK_COMPLETED.equals(type) || EVENT_TASK_CANCELLED.equals(type)
                || EVENT_TASK_UNBLOCKED.equals(type);
    }

    /**
     * isMemberEvent.
     * 
     * @param type type
     * @return the result
     * @since 0.1.7
     */
    private static boolean isMemberEvent(String type) {
        return EVENT_MEMBER_SPAWNED.equals(type) || EVENT_MEMBER_RESTARTED.equals(type)
                || EVENT_MEMBER_STATUS_CHANGED.equals(type) || EVENT_MEMBER_EXECUTION_CHANGED.equals(type)
                || EVENT_MEMBER_SHUTDOWN.equals(type) || EVENT_MEMBER_CANCELED.equals(type);
    }

    /**
     * appendTaskLines.
     * 
     * @param lines lines
     * @param tasks tasks
     * @since 0.1.7
     */
    private static void appendTaskLines(List<String> lines, List<TeamTask> tasks) {
        for (TeamTask task : tasks) {
            String assignee = task.getAssignee() != null && !task.getAssignee().isBlank()
                    ? " -> " + task.getAssignee()
                    : " (unassigned)";
            lines.add("- [" + task.getTaskId() + "] [" + task.getStatus() + "] " + task.getTitle() + ": "
                    + task.getContent() + assignee);
        }
    }
}
