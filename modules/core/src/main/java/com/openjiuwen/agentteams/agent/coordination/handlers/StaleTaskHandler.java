/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination.handlers;

import com.openjiuwen.agentteams.I18n;
import com.openjiuwen.agentteams.agent.coordination.DispatcherHost;
import com.openjiuwen.agentteams.agent.coordination.InnerEventType;
import com.openjiuwen.agentteams.agent.coordination.PollController;
import com.openjiuwen.agentteams.agent.coordination.TeamAgentBlueprint;
import com.openjiuwen.agentteams.agent.coordination.TeamInfra;
import com.openjiuwen.agentteams.schema.events.CoordinationEvent;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.timefmt.TimeFormat;
import com.openjiuwen.agentteams.tools.TeamMessageManager;
import com.openjiuwen.agentteams.tools.TeamTask;
import com.openjiuwen.agentteams.tools.TeamTaskManager;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Periodic stale-task sweep on POLL_TASK ticks.
 *
 * <p>Mirrors Python {@code handlers/stale_task.py}. Checks tasks stuck in
 * CLAIMED past the stale threshold (every member sweeps tasks assigned to
 * itself; leader additionally sweeps all members') and PENDING past the stale
 * threshold (leader only). Per-task throttle prevents re-nudging within one
 * stale window across both the poll path and the member-status-change path
 * ({@link MemberHandler}) — they share the same throttle map by reference.
 *
 * @since 2026/7/9
 */
public class StaleTaskHandler extends BaseCoordinationHandler {
    /** 10 minutes — stale-claim threshold. */
    static final long STALE_CLAIM_MILLIS = 10 * 60 * 1000L;

    /** 10 minutes — stale-pending threshold. */
    static final long STALE_PENDING_MILLIS = 10 * 60 * 1000L;

    /** task_id -&gt; wall-clock millis of last stale-pending nudge. Leader-only. */
    public final Map<String, Long> lastPendingNudge = new ConcurrentHashMap<>();

    /**
     * task_id -&gt; wall-clock millis of last stale-claim nudge. Shared with
     * {@link MemberHandler} so a poll tick and a status flip within the same
     * window cannot double-nudge.
     */
    final Map<String, Long> lastStaleNudge;

    /**
     * Construct with shared stale-claim throttle.
     *
     * @param host the owning TeamAgent
     * @param blueprint static config
     * @param infra per-process services
     * @param pollCtrl poll control surface
     * @param staleClaimThrottle shared throttle map (same instance as MemberHandler's)
     */
    public StaleTaskHandler(DispatcherHost host, TeamAgentBlueprint blueprint,
                            TeamInfra infra, PollController pollCtrl,
                            Map<String, Long> staleClaimThrottle) {
        super(host, blueprint, infra, pollCtrl);
        this.lastStaleNudge = staleClaimThrottle;
        callbacks.put(InnerEventType.POLL_TASK.getValue(), this::onPollTask);
    }

    /**
     * Periodic task-board sweep: flag stale CLAIMED + leader stale PENDING.
     *
     * @param event the poll task event
     */
    public void onPollTask(CoordinationEvent event) {
        String memberName = blueprint.memberName().orElse(null);
        Loggers.AGENT.debug("poll task: member_name={}, agent_running={}",
                memberName, round.isAgentRunning());
        if (memberName != null && !memberName.isBlank()) {
            checkStaleClaimedTasks();
            checkStalePendingTasks();
        }
    }

    /**
     * Find claimed tasks past the stale threshold.
     *
     * <p>Measures elapsed time by reading the database {@code updated_at}
     * column. Self-assignee stale claims nudge the local agent via
     * {@code deliver_input}; leader-observed stale claims on other members
     * nudge the assignee via {@code send_message}. Per-task throttle prevents
     * follow-up polls from re-nudging inside the same stale window. GCs
     * throttle entries for tasks no longer claimed.
     */
    public void checkStaleClaimedTasks() {
        String ownName = blueprint.memberName().orElse(null);
        boolean isLeader = blueprint.role().orElse(null) == TeamRole.LEADER;
        Object tm = infra.taskManager();
        if (!(tm instanceof TeamTaskManager taskManager)) {
            return;
        }
        List<TeamTask> relevant = taskManager.list().stream()
                .filter(task -> "claimed".equals(task.getStatus()))
                .filter(task -> task.getAssignee() != null && !task.getAssignee().isBlank())
                .filter(task -> isLeader || task.getAssignee().equals(ownName))
                .toList();

        // GC throttle entries for tasks no longer claimed-and-relevant.
        Set<String> currentIds = relevant.stream()
                .map(TeamTask::getTaskId).collect(Collectors.toSet());
        lastStaleNudge.keySet().removeIf(taskId -> !currentIds.contains(taskId));
        if (relevant.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (TeamTask task : relevant) {
            if (now - task.getUpdatedAt() < STALE_CLAIM_MILLIS) {
                continue;
            }
            if (now - lastStaleNudge.getOrDefault(task.getTaskId(), 0L) < STALE_CLAIM_MILLIS) {
                continue;
            }
            lastStaleNudge.put(task.getTaskId(), now);
            nudgeStaleClaim(task, now, ownName, isLeader);
        }
    }

    private void nudgeStaleClaim(TeamTask task, long nowMs, String ownName, boolean isLeader) {
        String content = formatStaleClaimNudge(task, nowMs);
        if (task.getAssignee().equals(ownName)) {
            round.deliverInput(content);
            Loggers.AGENT.info("[{}] self-nudged stale claimed task {}",
                    ownName, task.getTaskId());
        } else if (isLeader) {
            Object mm = infra.messageManager();
            if (mm instanceof TeamMessageManager mgr) {
                mgr.sendMessage(content, task.getAssignee()).join();
            }
            Loggers.AGENT.info("[leader] nudged {} about stale claimed task {}",
                    task.getAssignee(), task.getTaskId());
        } else {
            Loggers.AGENT.debug("Stale task {} owned by {}, no action for {}",
                    task.getTaskId(), task.getAssignee(), ownName);
        }
    }

    private static String formatStaleClaimNudge(TeamTask task, long nowMs) {
        String timeInfo = TimeFormat.formatTimeContext(task.getUpdatedAt(), nowMs);
        return I18n.t("dispatcher.stale_claim_self",
                task.getTaskId(), task.getTitle(), task.getContent(), timeInfo);
    }

    /**
     * Leader-only: self-prompt about pending tasks that nobody claimed.
     *
     * <p>Scans pending tasks via {@code updated_at}. When a task has been
     * pending past {@link #STALE_PENDING_MILLIS}, the leader feeds itself an
     * input listing those tasks plus a hint to pick the right teammate and
     * ping them via {@code send_message}. The model decides who to notify —
     * the dispatcher does not try to do the matching itself. Per-task throttle
     * prevents follow-up polls from re-prompting inside the same stale window.
     */
    public void checkStalePendingTasks() {
        if (blueprint.role().orElse(null) != TeamRole.LEADER) {
            return;
        }
        long now = System.currentTimeMillis();
        Object tm = infra.taskManager();
        if (!(tm instanceof TeamTaskManager taskManager)) {
            return;
        }
        List<TeamTask> pending = taskManager.list().stream()
                .filter(task -> "pending".equals(task.getStatus()))
                .toList();
        Set<String> staleIds = pending.stream()
                .filter(task -> now - task.getUpdatedAt() >= STALE_PENDING_MILLIS)
                .map(TeamTask::getTaskId)
                .collect(Collectors.toSet());

        // GC throttle entries for tasks no longer pending/stale.
        lastPendingNudge.keySet().removeIf(taskId -> !staleIds.contains(taskId));
        List<TeamTask> fresh = new ArrayList<>();
        for (TeamTask task : pending) {
            if (!staleIds.contains(task.getTaskId())) {
                continue;
            }
            if (now - lastPendingNudge.getOrDefault(task.getTaskId(), 0L) < STALE_PENDING_MILLIS) {
                continue;
            }
            fresh.add(task);
        }
        if (fresh.isEmpty()) {
            return;
        }
        for (TeamTask task : fresh) {
            lastPendingNudge.put(task.getTaskId(), now);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(I18n.t("dispatcher.stale_pending_header"));
        for (TeamTask task : fresh) {
            String timeInfo = TimeFormat.formatTimeContext(task.getUpdatedAt(), now);
            sb.append("\n- [").append(task.getTaskId()).append("] ")
                    .append(task.getTitle()).append(": ").append(task.getContent())
                    .append(" (").append(timeInfo).append(")");
        }
        round.deliverInput(sb.toString());
        Loggers.AGENT.info("[leader] self-prompted about {} stale pending task(s)", fresh.size());
    }
}
