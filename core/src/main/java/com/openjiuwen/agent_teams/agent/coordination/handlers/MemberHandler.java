/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination.handlers;

import com.openjiuwen.agent_teams.AgentTeamI18n;
import com.openjiuwen.agent_teams.AgentTeamTimefmt;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.CoordinationEvent;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.DispatcherHost;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.TransportEvent;
import com.openjiuwen.agent_teams.schema.BaseEventMessage;
import com.openjiuwen.agent_teams.schema.MemberShutdownEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.TeamTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles member lifecycle coordination events.
 *
 * <p>Mirrors Python's {@code MemberHandler} in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/member.py}.</p>
 */
public class MemberHandler extends BaseCoordinationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemberHandler.class);
    private static final Set<String> IDLE_NUDGE_STATUSES =
            Set.of(MemberStatus.READY.value(), MemberStatus.ERROR.value());
    private static final double STALE_CLAIM_SECONDS = 10 * 60.0d;

    private final Map<String, Double> lastStaleNudge;

    public MemberHandler(
            DispatcherHost host,
            TeamAgentBlueprint blueprint,
            TeamInfra infra,
            PollController pollController,
            Map<String, Double> staleClaimThrottle
    ) {
        super(host, blueprint, infra, pollController);
        this.lastStaleNudge = Objects.requireNonNull(staleClaimThrottle, "staleClaimThrottle");
    }

    @Override
    public Map<String, String> getEventMethodMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(TeamEvent.MEMBER_SPAWNED, "onMemberEvent");
        map.put(TeamEvent.MEMBER_RESTARTED, "onMemberEvent");
        map.put(TeamEvent.MEMBER_STATUS_CHANGED, "onMemberEvent");
        map.put(TeamEvent.MEMBER_EXECUTION_CHANGED, "onMemberEvent");
        map.put(TeamEvent.MEMBER_SHUTDOWN, "onMemberEvent");
        map.put(TeamEvent.MEMBER_CANCELED, "onMemberEvent");
        return map;
    }

    @Override
    protected EventCallback resolveCallback(String methodName) {
        if ("onMemberEvent".equals(methodName)) {
            return this::onMemberEvent;
        }
        throw new IllegalArgumentException("Unknown method: " + methodName);
    }

    public CompletionStage<Void> onMemberEvent(CoordinationEvent event) {
        if (blueprint.getRole() == TeamRole.LEADER) {
            return handleLeaderMemberEvent(event);
        }
        return handleTeammateMemberEvent(event);
    }

    public CompletionStage<Void> handleTeammateMemberEvent(CoordinationEvent event) {
        EventMessage message = messageOf(event);
        BaseEventMessage payload = message.getPayload();
        String memberName = blueprint.getMemberName();
        String targetId = payload.getMemberName();
        if (targetId == null || !targetId.equals(memberName)) {
            return CompletableFuture.completedFuture(null);
        }
        if (TeamEvent.MEMBER_CANCELED.equals(message.getEventType())) {
            return round.cancelAgent();
        }
        if (TeamEvent.MEMBER_SHUTDOWN.equals(message.getEventType()) && blueprint.getRole() == TeamRole.HUMAN_AGENT) {
            return shutdownHumanAgent(payload);
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> shutdownHumanAgent(BaseEventMessage payload) {
        boolean force = payload instanceof MemberShutdownEvent shutdown && shutdown.isForce();
        if (force || !round.hasInFlightRound()) {
            return lifecycle.shutdownSelf();
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> handleLeaderMemberEvent(CoordinationEvent event) {
        EventMessage message = messageOf(event);
        Map<String, Object> payload = message.getPayloadData();
        String targetId = String.valueOf(payload.getOrDefault("member_name", ""));
        String eventType = message.getEventType();
        CompletionStage<Void> followUp = CompletableFuture.completedFuture(null);
        String text;
        if (TeamEvent.MEMBER_SPAWNED.equals(eventType)) {
            text = AgentTeamI18n.t("dispatcher.member_online", "target_id", targetId);
        } else if (TeamEvent.MEMBER_RESTARTED.equals(eventType)) {
            Object restartCount = payload.getOrDefault("restart_count", 1);
            text = AgentTeamI18n.t(
                    "dispatcher.member_restarted",
                    "target_id",
                    targetId,
                    "restart_count",
                    restartCount
            );
        } else if (TeamEvent.MEMBER_STATUS_CHANGED.equals(eventType)) {
            String oldStatus = stringOrNull(payload.get("old_status"));
            String newStatus = stringOrNull(payload.get("new_status"));
            text = AgentTeamI18n.t(
                    "dispatcher.member_status_changed",
                    "target_id",
                    targetId,
                    "old_status",
                    oldStatus,
                    "new_status",
                    newStatus
            );
            followUp = nudgeIdleMemberWithStaleClaims(targetId, oldStatus, newStatus);
        } else if (TeamEvent.MEMBER_EXECUTION_CHANGED.equals(eventType)) {
            text = AgentTeamI18n.t(
                    "dispatcher.member_execution_changed",
                    "target_id",
                    targetId,
                    "old_status",
                    payload.get("old_status"),
                    "new_status",
                    payload.get("new_status")
            );
        } else if (TeamEvent.MEMBER_SHUTDOWN.equals(eventType)) {
            text = AgentTeamI18n.t("dispatcher.member_shutdown", "target_id", targetId);
        } else if (TeamEvent.MEMBER_CANCELED.equals(eventType)) {
            text = AgentTeamI18n.t("dispatcher.member_canceled", "target_id", targetId);
        } else {
            return CompletableFuture.completedFuture(null);
        }
        LOGGER.debug(text);
        return followUp;
    }

    public CompletionStage<Void> nudgeIdleMemberWithStaleClaims(
            String targetId,
            String oldStatus,
            String newStatus
    ) {
        if (targetId == null || targetId.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        if (!IDLE_NUDGE_STATUSES.contains(newStatus)) {
            return CompletableFuture.completedFuture(null);
        }
        if (Objects.equals(newStatus, oldStatus)) {
            return CompletableFuture.completedFuture(null);
        }
        if (!(infra.getTaskManager() instanceof TaskManager taskManager)
                || !(infra.getMessageManager() instanceof MessageManager messageManager)) {
            return CompletableFuture.completedFuture(null);
        }

        return taskManager.getTasksByAssignee(targetId, TaskStatus.CLAIMED.value())
                .thenCompose(claimed -> sendStaleClaimNudge(targetId, claimed, messageManager, newStatus));
    }

    public Map<String, Double> getLastStaleNudge() {
        return lastStaleNudge;
    }

    private CompletionStage<Void> sendStaleClaimNudge(
            String targetId,
            List<TeamTask> claimed,
            MessageManager messageManager,
            String newStatus
    ) {
        if (claimed == null || claimed.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        double nowSeconds = System.currentTimeMillis() / 1000.0d;
        long nowMs = System.currentTimeMillis();
        long thresholdMs = Math.round(STALE_CLAIM_SECONDS * 1000.0d);
        List<TeamTask> stale = new ArrayList<>();
        for (TeamTask task : claimed) {
            if (task.getUpdatedAt() == null) {
                continue;
            }
            if (nowMs - task.getUpdatedAt() < thresholdMs) {
                continue;
            }
            double lastNudge = lastStaleNudge.getOrDefault(task.getTaskId(), 0.0d);
            if (nowSeconds - lastNudge < STALE_CLAIM_SECONDS) {
                continue;
            }
            stale.add(task);
        }
        if (stale.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        for (TeamTask task : stale) {
            lastStaleNudge.put(task.getTaskId(), nowSeconds);
        }

        List<String> lines = new ArrayList<>();
        lines.add(AgentTeamI18n.t("dispatcher.stale_claim_header", "count", stale.size()));
        for (TeamTask task : stale) {
            String timeInfo = AgentTeamTimefmt.formatTimeContext(task.getUpdatedAt(), nowMs);
            lines.add("- [" + task.getTaskId() + "] " + task.getTitle()
                    + ": " + task.getContent() + " (" + timeInfo + ")");
        }
        LOGGER.info(
                "[leader] nudged {} about {} stale claimed task(s) after status -> {}",
                targetId,
                stale.size(),
                newStatus
        );
        return messageManager.sendMessage(String.join("\n", lines), targetId);
    }

    private static EventMessage messageOf(CoordinationEvent event) {
        return ((TransportEvent) event).getMessage();
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Task manager surface used for stale-claim nudges.
     *
     * <p>Mirrors Python's task-manager calls in
     * {@code openjiuwen/agent_teams/agent/coordination/handlers/member.py}.</p>
     */
    public interface TaskManager {
        CompletionStage<List<TeamTask>> getTasksByAssignee(String targetId, String status);
    }

    /**
     * Message manager surface used for stale-claim nudges.
     *
     * <p>Mirrors Python's message-manager calls in
     * {@code openjiuwen/agent_teams/agent/coordination/handlers/member.py}.</p>
     */
    public interface MessageManager {
        CompletionStage<Void> sendMessage(String content, String targetId);
    }
}
