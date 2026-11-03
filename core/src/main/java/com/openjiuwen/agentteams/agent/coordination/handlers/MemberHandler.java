/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination.handlers;

import com.openjiuwen.agentteams.I18n;
import com.openjiuwen.agentteams.agent.coordination.DispatcherHost;
import com.openjiuwen.agentteams.agent.coordination.PollController;
import com.openjiuwen.agentteams.agent.coordination.TeamAgentBlueprint;
import com.openjiuwen.agentteams.agent.coordination.TeamInfra;
import com.openjiuwen.agentteams.timefmt.TimeFormat;
import com.openjiuwen.agentteams.schema.events.CoordinationEvent;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamEvent;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.tools.TeamMessageManager;
import com.openjiuwen.agentteams.tools.TeamTask;
import com.openjiuwen.agentteams.tools.TeamTaskManager;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Handle MEMBER_* lifecycle events.
 *
 * <p>Mirrors Python {@code handlers/member.py}. Leader observes all members'
 * transitions; teammate only reacts to events targeting itself
 * ({@code MEMBER_CANCELED} cancels the local round). The on-shutdown mailbox
 * drain is <em>not</em> this handler's concern — {@link MessageHandler}
 * registers its own {@code MEMBER_SHUTDOWN} callback and the framework fans
 * out both.
 *
 * @since 2026/7/9
 */
public class MemberHandler extends BaseCoordinationHandler {
    /** 10 minutes — stale-claim nudge threshold. */
    static final long STALE_CLAIM_MILLIS = 10 * 60 * 1000L;

    private static final List<String> IDLE_NUDGE_STATUSES = List.of("ready", "error");

    /**
     * task_id -&gt; wall-clock millis when we last fired a stale-claim nudge.
     * Shared by reference with {@link StaleTaskHandler} so a member status flip
     * and a poll tick within the same window cannot double-nudge.
     */
    final Map<String, Long> lastStaleNudge;

    /**
     * Construct with shared stale-claim throttle.
     *
     * @param host the owning TeamAgent
     * @param blueprint static config
     * @param infra per-process services
     * @param pollCtrl poll control surface
     * @param staleClaimThrottle shared throttle map (same instance as StaleTaskHandler's)
     */
    public MemberHandler(DispatcherHost host, TeamAgentBlueprint blueprint,
                         TeamInfra infra, PollController pollCtrl,
                         Map<String, Long> staleClaimThrottle) {
        super(host, blueprint, infra, pollCtrl);
        this.lastStaleNudge = staleClaimThrottle;
        callbacks.put(TeamEvent.MEMBER_SPAWNED, this::onMemberEvent);
        callbacks.put(TeamEvent.MEMBER_RESTARTED, this::onMemberEvent);
        callbacks.put(TeamEvent.MEMBER_STATUS_CHANGED, this::onMemberEvent);
        callbacks.put(TeamEvent.MEMBER_EXECUTION_CHANGED, this::onMemberEvent);
        callbacks.put(TeamEvent.MEMBER_SHUTDOWN, this::onMemberEvent);
        callbacks.put(TeamEvent.MEMBER_CANCELED, this::onMemberEvent);
    }

    /**
     * Handle MEMBER_* lifecycle events.
     *
     * <p>Leader: observe all members' transitions. Teammate: only react to
     * events targeting self.
     *
     * @param event the member lifecycle event
     */
    public void onMemberEvent(CoordinationEvent event) {
        if (blueprint.role().orElse(null) == TeamRole.LEADER) {
            handleLeaderMemberEvent(event);
        } else {
            handleTeammateMemberEvent(event);
        }
    }

    private void handleLeaderMemberEvent(CoordinationEvent event) {
        if (!(event instanceof EventMessage msg)) {
            return;
        }
        Map<String, Object> payload = msg.getPayload() != null ? msg.getPayload() : Map.of();
        String targetId = str(payload, "member_name");
        String eventType = msg.getEventType();
        Loggers.AGENT.info("handleLeaderMemberEvent: eventType={} target={} thread={}",
                eventType, targetId, Thread.currentThread().getName());
        switch (eventType) {
            case TeamEvent.MEMBER_SPAWNED -> {
                Loggers.AGENT.info("handleLeaderMemberEvent: MEMBER_SPAWNED target={}", targetId);
                Loggers.AGENT.debug(I18n.t("dispatcher.member_online", targetId));
            }
            case TeamEvent.MEMBER_RESTARTED -> {
                int restartCount = toInt(payload.get("restart_count"), 1);
                Loggers.AGENT.debug(I18n.t("dispatcher.member_restarted", targetId, restartCount));
            }
            case TeamEvent.MEMBER_STATUS_CHANGED -> {
                String oldStatus = str(payload, "old_status");
                String newStatus = str(payload, "new_status");
                Loggers.AGENT.debug(
                        I18n.t("dispatcher.member_status_changed", targetId, oldStatus, newStatus));
                nudgeIdleMemberWithStaleClaims(targetId, oldStatus, newStatus);
            }
            case TeamEvent.MEMBER_EXECUTION_CHANGED -> {
                String oldStatus = str(payload, "old_status");
                String newStatus = str(payload, "new_status");
                Loggers.AGENT.debug(
                        I18n.t("dispatcher.member_execution_changed", targetId, oldStatus, newStatus));
            }
            case TeamEvent.MEMBER_SHUTDOWN ->
                    Loggers.AGENT.debug(I18n.t("dispatcher.member_shutdown", targetId));
            case TeamEvent.MEMBER_CANCELED ->
                    Loggers.AGENT.debug(I18n.t("dispatcher.member_canceled", targetId));
            default -> Loggers.AGENT.debug("Unhandled member event type");
        }
    }

    private void handleTeammateMemberEvent(CoordinationEvent event) {
        if (!(event instanceof EventMessage msg)) {
            return;
        }
        String memberName = blueprint.memberName().orElse(null);
        Map<String, Object> payload = msg.getPayload() != null ? msg.getPayload() : Map.of();
        String targetId = str(payload, "member_name");
        if (targetId == null || !targetId.equals(memberName)) {
            return;
        }
        String eventType = msg.getEventType();
        if (TeamEvent.MEMBER_CANCELED.equals(eventType)) {
            round.cancelAgent();
        } else if (TeamEvent.MEMBER_SHUTDOWN.equals(eventType)
                && blueprint.role().orElse(null) == TeamRole.HUMAN_AGENT) {
            shutdownHumanAgent(payload);
        } else {
            Loggers.AGENT.debug("Ignoring member event type={} for non-human-agent role",
                    eventType);
        }
    }

    /**
     * Tear a human-agent avatar down on its own shutdown event.
     *
     * <p>A human agent has no autonomous round, so it cannot ride the teammate
     * teardown path (mailbox drain → final round → round-end close_stream).
     * Force or idle: collapse the avatar directly via {@code shutdownSelf}.
     * Controller-driven round in flight and not forced: leave alone — the
     * round-end check closes the stream once that round finishes naturally.
     *
     * @param payload the event payload containing the {@code force} flag
     */
    private void shutdownHumanAgent(Map<String, Object> payload) {
        boolean isForce = bool(payload, "force");
        if (isForce || !round.hasInFlightRound()) {
            lifecycle.shutdownSelf();
        }
    }

    /**
     * Remind a member about long-claimed work on transition to READY/ERROR.
     *
     * <p>Only tasks whose claim has aged past {@link #STALE_CLAIM_MILLIS} are
     * included, and each task is throttled via {@link #lastStaleNudge} — shared
     * with the POLL_TASK path — so successive status flips or a concurrent poll
     * tick cannot re-nudge within one stale window.
     *
     * @param memberName the member to nudge
     * @param oldStatus the previous member status
     * @param newStatus the new member status
     */
    public void nudgeIdleMemberWithStaleClaims(String memberName, String oldStatus, String newStatus) {
        if (memberName == null || memberName.isBlank()) {
            return;
        }
        if (!IDLE_NUDGE_STATUSES.contains(newStatus)) {
            return;
        }
        if (Objects.equals(oldStatus, newStatus)) {
            return;
        }
        long now = System.currentTimeMillis();
        Object tm = infra.taskManager();
        if (!(tm instanceof TeamTaskManager taskManager)) {
            return;
        }
        List<TeamTask> claimed = taskManager.getTasksByAssignee(memberName, "claimed");
        List<TeamTask> stale = new ArrayList<>();
        for (TeamTask task : claimed) {
            if (now - task.getUpdatedAt() < STALE_CLAIM_MILLIS) {
                continue;
            }
            if (now - lastStaleNudge.getOrDefault(task.getTaskId(), 0L) < STALE_CLAIM_MILLIS) {
                continue;
            }
            stale.add(task);
        }
        if (stale.isEmpty()) {
            return;
        }
        for (TeamTask task : stale) {
            lastStaleNudge.put(task.getTaskId(), now);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(I18n.t("dispatcher.stale_claim_header", stale.size()));
        for (TeamTask task : stale) {
            String timeInfo = TimeFormat.formatTimeContext(task.getUpdatedAt(), now);
            sb.append("\n- [").append(task.getTaskId()).append("] ")
                    .append(task.getTitle()).append(": ").append(task.getContent())
                    .append(" (").append(timeInfo).append(")");
        }
        Object mm = infra.messageManager();
        if (mm instanceof TeamMessageManager mgr) {
            mgr.sendMessage(sb.toString(), memberName).join();
        }
        Loggers.AGENT.info("[leader] nudged {} about {} stale claimed task(s) after status -> {}",
                memberName, stale.size(), newStatus);
    }

    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }
}
